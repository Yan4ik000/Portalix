#!/usr/bin/env python3
import argparse
import json
import os
import signal
import subprocess
import tempfile
import uuid
from datetime import datetime, timezone
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parent.parent


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def select_profiles(catalog_file, profiles, compatibility=False):
    catalog = json.loads(catalog_file.read_text(encoding="utf-8"))
    available = [
        f"{loader}-{renderer}-{backend}"
        for loader in catalog["loaders"]
        for backend in catalog["backends"]
        for renderer in catalog["renderers"]
    ]
    eligible = [p for p in available if (p in catalog["unsupported"]) == compatibility]
    if not profiles:
        return eligible
    if len(set(profiles)) != len(profiles):
        raise ValueError("Duplicate test profiles")
    for profile in profiles:
        if profile not in available:
            raise ValueError(f"Unknown profile: {profile}")
        if profile not in eligible:
            raise ValueError(f"Profile {profile} does not belong to this matrix; use --compatibility only for unsupported profiles")
    return profiles


def new_run(profiles, report_root):
    return {
        "runId": str(uuid.uuid4()),
        "selectedProfiles": profiles,
        "status": "running",
        "startedAt": timestamp(),
        "completedAt": None,
        "profiles": [
            {"profile": p, "status": "pending", "log": str(report_root / p / "gradle.log"),
             "startedAt": None, "finishedAt": None, "exitCode": None}
            for p in profiles
        ],
    }


def write_run(run, report_root):
    report_root.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=report_root,
                                         suffix=".tmp", delete=False) as stream:
            temporary = Path(stream.name)
            json.dump(run, stream, indent=2, ensure_ascii=False, allow_nan=False)
            stream.write("\n")
        temporary.replace(report_root / "matrix.json")
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def matrix_passed(run, compatibility=False):
    expected = "unsupported" if compatibility else "passed"
    return run["status"] == "completed" and bool(run["profiles"]) and all(
        entry["status"] == expected and entry["exitCode"] is not None
        and (entry["exitCode"] != 0 if compatibility else entry["exitCode"] == 0)
        for entry in run["profiles"]
    )


def report_status(path, profile, client_test, exit_code):
    if not path.exists():
        return "launch-failed"
    try:
        result = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError):
        return "invalid-report"
    if not isinstance(result, dict) or result.get("profile") != profile:
        return "invalid-report"
    status = result.get("status")
    if status not in ("passed", "failed", "incomplete", "unsupported"):
        return "invalid-report"
    if status in ("passed", "incomplete") and result.get("clientTest") != client_test:
        return "invalid-report"
    if status == "passed" and exit_code != 0:
        return "failed"
    return status


def stop_process(process):
    if os.name == "nt":
        subprocess.run(["taskkill.exe", "/PID", str(process.pid), "/T", "/F"],
                       stdout=subprocess.DEVNULL, check=True)
    else:
        # Gradle puts its single-use daemon in a separate process group.
        children = {}
        for line in subprocess.check_output(["ps", "-e", "-o", "pid=,ppid="], text=True).splitlines():
            pid, parent = map(int, line.split())
            children.setdefault(parent, []).append(pid)
        descendants = [process.pid]
        for pid in descendants:
            descendants.extend(children.get(pid, []))
        for pid in reversed(descendants):
            try:
                os.kill(pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    process.wait()


def run_matrix(repository, args, profiles):
    report_root = repository / "tests/build" / args.mc_version / "reports/regression"
    if args.packaged:
        report_root = report_root / "packaged"
    run = new_run(profiles, report_root)
    run.update(minecraftVersion=args.mc_version, clientTest=args.client_test, packaged=args.packaged,
               expectedStatus="unsupported" if args.compatibility else "passed")
    write_run(run, report_root)
    verified_loaders = set()
    try:
        for entry in run["profiles"]:
            profile = entry["profile"]
            entry.update(status="running", startedAt=timestamp())
            write_run(run, report_root)
            directory = report_root / profile
            directory.mkdir(parents=True, exist_ok=True)
            result_file = directory / "results.json"
            result_file.unlink(missing_ok=True)
            loader = profile.split("-", 1)[0]
            task = ":testClient" if args.compatibility or loader in verified_loaders else ":testProfile"
            wrapper = repository / ("gradlew.bat" if os.name == "nt" else "gradlew")
            command = [str(wrapper), task, f"-PmcVersion={args.mc_version}", f"-PtestProfile={profile}",
                       f"-PclientTest={args.client_test}", f"-PtestDiagnostics={args.diagnostics}",
                       f"-PtestPackaged={str(args.packaged).lower()}",
                       "--no-daemon", "--console=plain"]
            print(f"Running {profile}", flush=True)
            with (directory / "gradle.log").open("wb") as stdout, (directory / "gradle-error.log").open("wb") as stderr:
                try:
                    process = subprocess.Popen(command, cwd=repository, stdout=stdout, stderr=stderr,
                                               start_new_session=os.name != "nt")
                except OSError as error:
                    stderr.write(f"{error}\n".encode("utf-8"))
                    entry["status"] = "launch-failed"
                else:
                    try:
                        entry["exitCode"] = process.wait(timeout=args.timeout_seconds)
                    except subprocess.TimeoutExpired:
                        stop_process(process)
                        entry["status"] = "timeout"
                    except KeyboardInterrupt:
                        stop_process(process)
                        entry.update(status="interrupted", finishedAt=timestamp())
                        raise
                    else:
                        entry["status"] = report_status(result_file, profile, args.client_test, process.returncode)
            if entry["status"] == "passed":
                verified_loaders.add(loader)
            entry["finishedAt"] = timestamp()
            print(f"{profile}: {entry['status']}", flush=True)
            write_run(run, report_root)
    except KeyboardInterrupt:
        run["status"] = "interrupted"
        raise
    except OSError:
        run["status"] = "failed"
        raise
    else:
        run["status"] = "completed"
    finally:
        run["completedAt"] = timestamp()
        write_run(run, report_root)
    return 0 if matrix_passed(run, args.compatibility) else 1


def main():
    parser = argparse.ArgumentParser(description="Run the Fabric/NeoForge regression matrix.")
    versions_dir = REPOSITORY / "versions"
    versions = sorted(path.parent.name for path in versions_dir.glob("*/gradle.properties"))
    default_version = None
    for line in (REPOSITORY / "gradle.properties").read_text(encoding="utf-8").splitlines():
        key, _, value = line.partition("=")
        if key.strip() == "default_minecraft_version":
            default_version = value.strip()
            break
    if default_version not in versions:
        parser.error("default_minecraft_version in gradle.properties must match a version directory")
    parser.add_argument("--mc-version", choices=versions, default=default_version,
                        help="Minecraft version (default: %(default)s)")
    parser.add_argument("--profiles", nargs="+", default=[])
    parser.add_argument("--client-test", default="all")
    parser.add_argument("--diagnostics", choices=("compact", "full"), default="compact")
    parser.add_argument("--compatibility", action="store_true")
    parser.add_argument("--packaged", action="store_true", help="Load and verify the built release JAR instead of mod class directories")
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--timeout-seconds", type=int, default=1800)
    args = parser.parse_args()
    if not 60 <= args.timeout_seconds <= 7200:
        parser.error("--timeout-seconds must be between 60 and 7200")
    version_dir = versions_dir / args.mc_version
    try:
        profiles = select_profiles(version_dir / "tests/test-profiles.json", args.profiles, args.compatibility)
    except ValueError as error:
        parser.error(str(error))
    if args.list:
        print("\n".join(profiles))
        return 0
    scene_path = Path("tests/src/client/resources/portalix-regression-scenes.json")
    version_scene_path = version_dir / "tests/client/resources" / scene_path.name
    scene_files = [path for path in (REPOSITORY / scene_path, version_scene_path) if path.is_file()]
    if len(scene_files) != 1:
        parser.error(f"Expected exactly one {scene_path} in shared or Minecraft {args.mc_version} sources")
    scenes = json.loads(scene_files[0].read_text(encoding="utf-8"))
    if args.client_test != "all" and args.client_test not in scenes["scenes"]:
        parser.error(f"Unknown client system: {args.client_test}")
    try:
        return run_matrix(REPOSITORY, args, profiles)
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
