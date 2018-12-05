package org.gradoop.demo.server.pojo.enums;

public class VertexDegreeType {

  public enum Type {
    IN,
    OUT,
    BOTH
  }

  private Type type;

  private VertexDegreeType(Type type) {
    this.type = type;
  }

  public static VertexDegreeType fromString(String typeString) {
    switch (typeString) {
      case "in":
        return new VertexDegreeType(Type.IN);
      case "out":
        return new VertexDegreeType(Type.OUT);
      case "both":
        return new VertexDegreeType(Type.BOTH);
      default:
        throw new IllegalArgumentException(
          "Vertex degree type '" + typeString + "' was not recognized.");
    }
  }

  public Type getType() {
    return this.type;
  }
}
