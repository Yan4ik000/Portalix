package dev.portalix.client.portal.clip;

import dev.portalix.client.render.GlslSource;
import dev.portalix.client.render.GlslSource.Token;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PortalShaderSource {
  private record Edit(int start, int end, String text) {}

  private PortalShaderSource() {}

  static String geometry(String source, String clip) {
    var parsed = new GlslSource(source);
    var tokens = parsed.tokens();
    var edits = new ArrayList<Edit>();
    edits.add(
        new Edit(tokens.getFirst().start(), tokens.getFirst().start(), "void " + clip + "();\n"));
    for (int index = 0; index + 1 < tokens.size(); index++) {
      String name = tokens.get(index).text();
      if ((!name.equals("EmitVertex") && !name.equals("EmitStreamVertex"))
          || !tokens.get(index + 1).text().equals("(")) continue;
      int end = parsed.closingParenthesis(index + 1);
      edits.add(new Edit(tokens.get(index).start(), tokens.get(index).start(), "(" + clip + "(), "));
      edits.add(new Edit(tokens.get(end).end(), tokens.get(end).end(), ")"));
    }
    return apply(source, edits);
  }

  static String clipDistances(String source) {
    var tokens = new GlslSource(source).tokens();
    var edits = new ArrayList<Edit>();
    for (int index = 0; index + 2 < tokens.size(); index++) {
      if (!tokens.get(index).text().equals("out")) continue;
      if (tokens.get(index + 1).text().equals("gl_PerVertex")) {
        int end = index + 3;
        while (end < tokens.size() && !tokens.get(end).text().equals("}")) end++;
        int member = index + 3;
        while (member < end && !tokens.get(member).text().equals("gl_ClipDistance")) member++;
        if (member == end)
          edits.add(
              new Edit(
                  tokens.get(end).start(), tokens.get(end).start(), "float gl_ClipDistance[2];\n"));
        else resizeClipDistance(tokens, member, source, edits);
      } else if (tokens.get(index + 1).text().equals("float")
          && tokens.get(index + 2).text().equals("gl_ClipDistance")) {
        resizeClipDistance(tokens, index + 2, source, edits);
      }
    }
    return apply(source, edits);
  }

  private static void resizeClipDistance(
      List<Token> tokens, int member, String source, List<Edit> edits) {
    int start = member + 1, end = start + 1;
    while (!tokens.get(end).text().equals("]")) end++;
    String size = source.substring(tokens.get(start).end(), tokens.get(end).start()).trim();
    if (!size.isEmpty())
      edits.add(new Edit(tokens.get(start).end(), tokens.get(end).start(), "max(2, (" + size + "))"));
  }

  private static String apply(String source, List<Edit> edits) {
    edits.sort(Comparator.comparingInt(Edit::start).reversed());
    var result = new StringBuilder(source);
    for (var edit : edits) result.replace(edit.start, edit.end, edit.text);
    return result.toString();
  }
}
