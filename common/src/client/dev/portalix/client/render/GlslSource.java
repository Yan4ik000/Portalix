package dev.portalix.client.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GlslSource {
  public record Token(String text, int start, int end) {}

  private final List<Token> tokens;
  private final Set<String> identifiers;

  public GlslSource(String source) {
    var tokens = new ArrayList<Token>();
    var identifiers = new HashSet<String>();
    boolean directive = false;
    for (int index = 0; index < source.length(); ) {
      char current = source.charAt(index);
      if (current == '\n') {
        int previous = index - 1;
        if (previous >= 0 && source.charAt(previous) == '\r') previous--;
        if (previous < 0 || source.charAt(previous) != '\\') directive = false;
        index++;
      } else if (Character.isWhitespace(current)) {
        index++;
      } else if (source.startsWith("//", index)) {
        int end = source.indexOf('\n', index);
        index = end < 0 ? source.length() : end;
      } else if (source.startsWith("/*", index)) {
        int end = source.indexOf("*/", index + 2);
        int next = end < 0 ? source.length() : end + 2;
        int newline = source.indexOf('\n', index);
        if (newline >= 0 && newline < next) directive = false;
        index = next;
      } else if (current == '#') {
        directive = true;
        index++;
      } else if (current == '"') {
        index++;
        while (index < source.length()) {
          char quoted = source.charAt(index++);
          if (quoted == '"') break;
          if (quoted == '\\' && index < source.length()) index++;
        }
      } else {
        int start = index++;
        boolean identifier = Character.isJavaIdentifierStart(current);
        if (identifier)
          while (index < source.length() && Character.isJavaIdentifierPart(source.charAt(index)))
            index++;
        String text = source.substring(start, index);
        if (identifier) identifiers.add(text);
        if (!directive) tokens.add(new Token(text, start, index));
      }
    }
    this.tokens = List.copyOf(tokens);
    this.identifiers = Set.copyOf(identifiers);
  }

  public List<Token> tokens() {
    return tokens;
  }

  public boolean hasIdentifier(String identifier) {
    return identifiers.contains(identifier);
  }

  public boolean hasDeclaration(String type, String name) {
    for (int index = 0; index + 2 < tokens.size(); index++) {
      if (!tokens.get(index).text.equals(type) || !tokens.get(index + 1).text.equals(name))
        continue;
      String next = tokens.get(index + 2).text;
      if (next.equals(";") || next.equals("=") || next.equals("[")) return true;
    }
    return false;
  }

  public boolean hasUniformBlock(String name, String instance, Map<String, String> members) {
    for (int index = 0; index + 2 < tokens.size(); index++) {
      if (!tokens.get(index).text.equals("uniform")
          || !tokens.get(index + 1).text.equals(name)
          || !tokens.get(index + 2).text.equals("{")) continue;
      int end = index + 3;
      int depth = 1;
      int found = 0;
      while (end < tokens.size() && depth > 0) {
        String token = tokens.get(end).text;
        if (token.equals("{")) depth++;
        else if (token.equals("}")) depth--;
        else if (depth == 1 && end + 2 < tokens.size()
            && token.equals(members.get(tokens.get(end + 1).text))
            && tokens.get(end + 2).text.equals(";")) found++;
        end++;
      }
      if (depth != 0 || found != members.size() || end >= tokens.size()) return false;
      if (instance == null) return tokens.get(end).text.equals(";");
      return tokens.get(end).text.equals(instance)
          && end + 1 < tokens.size() && tokens.get(end + 1).text.equals(";");
    }
    return false;
  }

  public String uniqueIdentifier(String preferred) {
    String candidate = preferred;
    for (int suffix = 1; identifiers.contains(candidate); suffix++) candidate = preferred + suffix;
    return candidate;
  }

  public int function(String returnType, String name, String... parameters) {
    for (int index = 0; index + 3 < tokens.size(); index++) {
      if (!tokens.get(index).text.equals(returnType)
          || !tokens.get(index + 1).text.equals(name)
          || !tokens.get(index + 2).text.equals("(")) continue;
      int end = closingParenthesis(index + 2);
      int cursor = index + 3;
      if (parameters.length == 0 && cursor + 1 == end && tokens.get(cursor).text.equals("void"))
        cursor++;
      boolean matches = true;
      for (int parameter = 0; parameter < parameters.length; parameter++) {
        if (cursor + 1 >= end || !tokens.get(cursor).text.equals(parameters[parameter])) {
          matches = false;
          break;
        }
        cursor += 2;
        if (parameter + 1 < parameters.length) {
          if (cursor >= end || !tokens.get(cursor).text.equals(",")) {
            matches = false;
            break;
          }
          cursor++;
        }
      }
      if (matches && cursor == end && end + 1 < tokens.size()
          && tokens.get(end + 1).text.equals("{"))
        return tokens.get(index + 1).start;
    }
    return -1;
  }

  public int closingParenthesis(int start) {
    int depth = 0;
    for (int index = start; index < tokens.size(); index++) {
      String token = tokens.get(index).text;
      if (token.equals("(")) depth++;
      else if (token.equals(")") && --depth == 0) return index;
    }
    throw new IllegalArgumentException("Unclosed shader function call");
  }
}
