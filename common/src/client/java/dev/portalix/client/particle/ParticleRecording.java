package dev.portalix.client.particle;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.joml.Vector3f;

final class ParticleRecording {
  private final List<Frame> frames;
  private final float duration;

  public ParticleRecording(InputStream source) throws IOException {
    try (DataInputStream in = new DataInputStream(new GZIPInputStream(source))) {
      if (in.readInt() != 1346850865) {
        throw new IOException("Invalid Source particle recording");
      }

      int count = in.readInt();
      if (count < 2 || count > 36000) {
        throw new IOException("Invalid Source frame count");
      }

      this.frames = new ArrayList<>(count);

      for (int f = 0; f < count; f++) {
        float time = in.readFloat();
        if (!Float.isFinite(time) || time < 0.0F || f > 0 && time <= this.frames.getLast().time()) {
          throw new IOException("Nonmonotonic Source frame time");
        }

        int tracks = in.readInt();
        if (tracks < 0 || tracks > 128) {
          throw new IOException("Invalid Source track count");
        }

        List<Track> list = new ArrayList<>();

        for (int s = 0; s < tracks; s++) {
          String name = in.readUTF();
          int particles = in.readInt();
          if (particles < 0 || particles > 5000) {
            throw new IOException("Invalid Source particle count");
          }

          List<Particle> values = new ArrayList<>();

          for (int n = 0; n < particles; n++) {
            long id = in.readLong();
            float[] w = new float[4];

            for (int i = 0; i < 4; i++) {
              w[i] = in.readFloat();
            }

            float radius = in.readFloat();
            float red = in.readFloat();
            float green = in.readFloat();
            float blue = in.readFloat();
            float alpha = in.readFloat();
            float rotation = in.readFloat();
            int sequence = in.readInt();
            float age = in.readFloat();

            for (float value :
                new float[] {
                  w[0], w[1], w[2], w[3], radius, red, green, blue, alpha, rotation, age
                }) {
              if (!Float.isFinite(value)) {
                throw new IOException("Nonfinite Source attribute");
              }
            }

            if (Math.abs(w[0] + w[1] + w[2] + w[3] - 1.0F) > 0.01F || radius < 0.0F) {
              throw new IOException("Invalid Source particle coordinates");
            }

            values.add(
                new Particle(id, w, radius, red, green, blue, alpha, rotation, sequence, age));
          }

          list.add(new Track(name, values));
        }

        this.frames.add(new Frame(time, list));
      }

      if (in.read() != -1) {
        throw new IOException("Trailing Source recording data");
      }

      this.duration = this.frames.getLast().time();
    }
  }

  ParticleRecording(List<Frame> recorded) {
    this.frames = List.copyOf(recorded);
    this.duration = this.frames.getLast().time();
  }

  List<Frame> recordedFrames() {
    return this.frames;
  }

  public float duration() {
    return this.duration;
  }

  interface Attributes {
    long id();

    float radius();

    float red();

    float green();

    float blue();

    float alpha();

    float rotation();

    int sequence();

    float age();

    default Vector3f position(Vector3f[] points) {
      return position(points, new Vector3f());
    }

    Vector3f position(Vector3f[] points, Vector3f destination);
  }

  public record Frame(float time, List<Track> tracks) {
    public Frame {
      tracks = List.copyOf(tracks);
    }
  }

  public record Particle(
      long id,
      float[] weights,
      float radius,
      float red,
      float green,
      float blue,
      float alpha,
      float rotation,
      int sequence,
      float age)
      implements Attributes {
    public Particle {
      if (weights.length != 4) {
        throw new IllegalArgumentException("Four control-point weights required");
      }

      weights = weights.clone();
    }

    public float[] weights() {
      return this.weights.clone();
    }

    @Override
    public Vector3f position(Vector3f[] points, Vector3f destination) {
      destination.zero();

      for (int i = 0; i < 4; i++) {
        destination.fma(this.weights[i], points[i]);
      }

      return destination;
    }
  }

  public record Track(String system, List<Particle> particles) {
    public Track {
      particles = List.copyOf(particles);
    }
  }
}
