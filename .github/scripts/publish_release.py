import argparse
import hashlib
import json
import os
import re
import subprocess
import tempfile
import tomllib
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote, urlencode
from zipfile import BadZipFile, ZipFile


ROOT = Path(__file__).resolve().parents[2]
CURSEFORGE = "https://minecraft.curseforge.com/api"
CURSEFORGE_PROJECT_ID = 1786970
MODRINTH = "https://api.modrinth.com/v2"
USER_AGENT = "Yan4ik000/Portalix release publisher (https://github.com/Yan4ik000/Portalix)"
LOADER_NAMES = {"fabric": "Fabric", "neoforge": "NeoForge"}


def gradle_property(path, name):
    value = re.search(rf"^[ \t]*{name}[ \t]*=[ \t]*(\S+)[ \t]*$", path.read_text(encoding="utf-8"), re.M)
    if value is None:
        raise ValueError(f"Missing {name} in {path}")
    return value[1]


@dataclass(frozen=True)
class ReleaseJar:
    name: str
    loader: str
    minecraft: str
    version: str
    java: int
    size: int
    digest: str | None
    fabric_api: str | None

    def validate(self, path):
        if path.stat().st_size != self.size:
            raise ValueError(f"{self.name}: size differs from the GitHub release asset")
        if self.digest:
            with path.open("rb") as file:
                sha256 = hashlib.file_digest(file, "sha256").hexdigest()
            if self.digest != f"sha256:{sha256}":
                raise ValueError(f"{self.name}: SHA-256 differs from the GitHub release asset")

        with ZipFile(path) as jar:
            if self.loader == "fabric":
                metadata = json.loads(jar.read("fabric.mod.json"))
                depends = metadata["depends"]
                matches = (
                    metadata["id"] == "portalix"
                    and metadata["version"] == self.version
                    and depends["minecraft"] == self.minecraft
                    and depends["java"] == f">={self.java}"
                    and depends["fabric-api"] == f">={self.fabric_api.split('+', 1)[0]}"
                )
            else:
                metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8"))
                matches = any(mod["modId"] == "portalix" and mod["version"] == self.version for mod in metadata["mods"])
                matches = matches and any(
                    dep["modId"] == "minecraft" and dep["versionRange"] == f"[{self.minecraft}]"
                    for dep in metadata["dependencies"]["portalix"]
                )
            if not matches:
                raise ValueError(f"{self.name}: mod metadata differs from the release configuration")

            with jar.open("dev/portalix/PortalixMod.class") as class_file:
                header = class_file.read(8)
            if header[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(header[6:8], "big") != self.java + 44:
                raise ValueError(f"{self.name}: expected Java {self.java} bytecode")


def release_jars(release, source=ROOT):
    properties = source / "gradle.properties"
    version = gradle_property(properties, "version")
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)+", version):
        raise ValueError(f"Expected a numeric Portalix version, got {version!r}")
    java = int(gradle_property(properties, "java_version"))
    if release["tag_name"] not in {version, f"v{version}", f"v.{version}"}:
        raise ValueError(f"Release tag {release['tag_name']} differs from Gradle version {version}")

    artifacts = []
    pattern = re.compile(rf"Portalix-(fabric|neoforge)-([0-9][A-Za-z0-9.-]*)-{re.escape(version)}\.jar")
    for asset in release["assets"]:
        match = pattern.fullmatch(asset["name"])
        if match is None:
            continue
        loader, minecraft = match.groups()
        properties = source / "versions" / minecraft / "gradle.properties"
        if not properties.is_file():
            raise ValueError(f"{asset['name']}: no Gradle configuration for Minecraft {minecraft}")
        if asset["state"] != "uploaded":
            raise ValueError(f"{asset['name']}: GitHub has not finished receiving the file")
        fabric_api = gradle_property(properties, "fabric_api_version") if loader == "fabric" else None
        artifacts.append(ReleaseJar(
            asset["name"], loader, minecraft, version, java, asset["size"], asset.get("digest"), fabric_api,
        ))
    if not artifacts:
        raise ValueError("Attach the Portalix release JARs before publishing the GitHub release")
    return artifacts


def request_json(url, headers=(), form=()):
    command = ["curl", "--silent", "--show-error", "--connect-timeout", "15", "--max-time", "180",
               "--user-agent", USER_AGENT, "--header", "@-", "--write-out", "\n%{response_code}"]
    for field in form:
        command.extend(("--form", field))
    result = subprocess.run(command + [url], input="\n".join(headers), capture_output=True, text=True, encoding="utf-8")
    if result.returncode:
        raise ValueError(f"Request to {url} failed: {result.stderr.strip()}")
    body, _, status = result.stdout.rpartition("\n")
    if not status.startswith("2"):
        raise ValueError(f"{url} returned HTTP {status}: {body[:500]}")
    return json.loads(body)


def setting(name):
    value = os.environ.get(name)
    if not value:
        raise ValueError(f"Configure {name} in GitHub Actions")
    return value


def upload(url, headers, artifact_path, metadata_field, metadata):
    with tempfile.TemporaryDirectory() as directory:
        metadata_path = Path(directory) / "metadata.json"
        metadata_path.write_text(json.dumps(metadata, ensure_ascii=False), encoding="utf-8")
        result = request_json(url, headers, (
            f"{metadata_field}=<{metadata_path};type=application/json",
            f"file=@{artifact_path};type=application/java-archive",
        ))
    print(f"Uploaded {artifact_path.name} to {url}: {result['id']}")


def publish_curseforge(artifact, path, changelog, channel):
    headers = [f"X-Api-Token: {setting('CURSEFORGE_TOKEN')}"]
    types = request_json(f"{CURSEFORGE}/game/version-types", headers)
    versions = request_json(f"{CURSEFORGE}/game/versions", headers)
    type_slugs = {version_type["id"]: version_type["slug"] for version_type in types}
    tags = [
        ("minecraft", artifact.minecraft),
        ("modloader", LOADER_NAMES[artifact.loader]),
        ("environment", "Client"),
        ("environment", "Server"),
        ("java", f"Java {artifact.java}"),
    ]
    version_ids = []
    for category, name in tags:
        matching_types = {
            type_id for type_id, slug in type_slugs.items()
            if slug == category or (category == "minecraft" and slug.startswith("minecraft-"))
        }
        named_tags = [row for row in versions if row["name"].casefold() == name.casefold()]
        matches = [row["id"] for row in named_tags if row["gameVersionTypeID"] in matching_types]
        if len(matches) != 1:
            options = [(row["id"], type_slugs.get(row["gameVersionTypeID"], row["gameVersionTypeID"])) for row in named_tags]
            raise ValueError(f"Expected one CurseForge {category} tag named {name!r}, found {len(matches)}. Catalog: {options}")
        version_ids.append(matches[0])

    upload(f"{CURSEFORGE}/projects/{CURSEFORGE_PROJECT_ID}/upload-file", headers, path, "metadata", {
        "displayName": artifact.name,
        "changelog": changelog,
        "changelogType": "markdown",
        "releaseType": channel,
        "isMarkedForManualRelease": False,
        "gameVersions": version_ids,
    })


def publish_modrinth(artifact, path, changelog, channel):
    project = setting("MODRINTH_PROJECT_ID")
    if not re.fullmatch(r"[A-Za-z0-9]+", project):
        raise ValueError("MODRINTH_PROJECT_ID must be the Modrinth project ID")
    headers = [f"Authorization: {setting('MODRINTH_TOKEN')}"]
    query = urlencode({"game_versions": json.dumps([artifact.minecraft]), "loaders": json.dumps([artifact.loader])})
    versions = request_json(f"{MODRINTH}/project/{project}/version?{query}", headers)
    for version in versions:
        if version["version_number"] != artifact.version:
            continue
        with path.open("rb") as file:
            sha512 = hashlib.file_digest(file, "sha512").hexdigest()
        if any(item["hashes"]["sha512"] == sha512 for item in version["files"]):
            print(f"{artifact.name} is already on Modrinth: {version['id']}")
            return
        raise ValueError(f"Modrinth version {artifact.version} already exists with a different JAR for "
                         f"{artifact.loader} {artifact.minecraft}")

    dependencies = []
    if artifact.fabric_api:
        versions = request_json(f"{MODRINTH}/project/fabric-api/version?{query}")
        matches = [item for item in versions if item["version_number"] == artifact.fabric_api]
        if len(matches) != 1:
            raise ValueError(f"Expected one Modrinth Fabric API release {artifact.fabric_api!r}, found {len(matches)}")
        dependency = matches[0]
        dependencies.append({
            "project_id": dependency["project_id"],
            "version_id": dependency["id"],
            "dependency_type": "required",
        })

    upload(f"{MODRINTH}/version", headers, path, "data", {
        "project_id": project,
        "name": f"Portalix v{artifact.version}",
        "version_number": artifact.version,
        "version_type": channel,
        "changelog": changelog,
        "loaders": [artifact.loader],
        "game_versions": [artifact.minecraft],
        "environment": "client_and_server",
        "dependencies": dependencies,
        "featured": False,
        "file_parts": ["file"],
        "primary_file": "file",
    })


def release_info():
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    if "release" in event:
        return event["release"]

    tag = quote(setting("RELEASE_TAG"), safe="")
    repository = setting("GITHUB_REPOSITORY")
    result = subprocess.run(["gh", "api", f"repos/{repository}/releases/tags/{tag}"],
                            capture_output=True, text=True, encoding="utf-8")
    if result.returncode:
        raise ValueError(f"Could not read the GitHub release: {result.stderr.strip()}")
    release = json.loads(result.stdout)
    if release["draft"]:
        raise ValueError("Publish the GitHub release before uploading its JARs")
    return release


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("plan", "curseforge", "modrinth"))
    parser.add_argument("path", type=Path)
    args = parser.parse_args()

    release = release_info()
    source = Path(os.environ.get("RELEASE_SOURCE", ROOT))
    artifacts = release_jars(release, source)
    if args.command == "plan":
        for artifact in artifacts:
            artifact.validate(args.path / artifact.name)
        print(json.dumps([artifact.name for artifact in artifacts]))
        return

    artifact = next((jar for jar in artifacts if jar.name == args.path.name), None)
    if artifact is None:
        raise ValueError(f"{args.path.name} is not one of the release JARs")
    artifact.validate(args.path)
    changelog = release["body"] or ""
    channel = "beta" if release["prerelease"] else "release"

    if args.command == "curseforge":
        publish_curseforge(artifact, args.path, changelog, channel)
    else:
        publish_modrinth(artifact, args.path, changelog, channel)


if __name__ == "__main__":
    try:
        main()
    except (BadZipFile, KeyError, OSError, ValueError) as error:
        raise SystemExit(str(error)) from error
