package org.gradoop.demo.server.pojo.enums;

public class NeighborhoodType {
  public enum Type {
    IN,
    OUT,
    BOTH
  }

  private Type type;

  private NeighborhoodType(Type type) {
    this.type = type;
  }

  public static NeighborhoodType fromString(String typeString) {
    switch (typeString) {
      case "in":
        return new NeighborhoodType(Type.IN);
      case "out":
        return new NeighborhoodType(Type.OUT);
      case "both":
        return new NeighborhoodType(Type.BOTH);
      default:
        throw new IllegalArgumentException(
          "Neighborhood type '" + typeString + "' was not recognized.");
    }
  }

  public Type getType() {
    return this.type;
  }
}
