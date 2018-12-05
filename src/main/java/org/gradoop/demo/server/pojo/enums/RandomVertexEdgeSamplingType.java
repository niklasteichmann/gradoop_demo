package org.gradoop.demo.server.pojo.enums;

public class RandomVertexEdgeSamplingType {

  public enum Type {
    SIMPLE_VERSION,
    NONUNIFORM_VERSION,
    NONUNIFORM_HYBRID_VERSION
  }

  private Type type;

  private RandomVertexEdgeSamplingType(Type type) {
    this.type = type;
  }

  public static RandomVertexEdgeSamplingType fromString(String typeString) {
    switch (typeString) {
      case "simple":
        return new RandomVertexEdgeSamplingType(Type.SIMPLE_VERSION);
      case "nonuniform":
        return new RandomVertexEdgeSamplingType(Type.NONUNIFORM_VERSION);
      case "nonuniform_hybrid":
        return new RandomVertexEdgeSamplingType(Type.NONUNIFORM_HYBRID_VERSION);
      default:
        throw new IllegalArgumentException(
            "Random vertex edge sampling type '" + typeString + "' was not recognized.");
    }
  }

  public Type getType() {
    return this.type;
  }
}
