package nomad.campaign.econ;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CsvText {

  private CsvText() {
  }

  public static List<Map<String, String>> rows(String path, List<String> problems) {
    List<Map<String, String>> out = new ArrayList<Map<String, String>>();
    String text;
    try {
      text = new String(Files.readAllBytes(Paths.get(path)), "UTF-8");
    } catch (IOException e) {
      problems.add("could not read " + path + " (" + e + ")");
      return out;
    }
    List<List<String>> records = records(text);
    if (records.isEmpty()) {
      problems.add(path + " has no header row");
      return out;
    }
    List<String> header = records.get(0);
    for (int r = 1; r < records.size(); r++) {
      List<String> record = records.get(r);
      Map<String, String> cells = new LinkedHashMap<String, String>();
      for (int i = 0; i < header.size() && i < record.size(); i++) {
        cells.put(header.get(i).trim(), record.get(i));
      }
      out.add(cells);
    }
    return out;
  }

  static List<List<String>> records(String text) {
    List<List<String>> records = new ArrayList<List<String>>();
    List<String> field = new ArrayList<String>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (inQuotes) {
        if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
          cur.append('"');
          i++;
        } else if (ch == '"') {
          inQuotes = false;
        } else {
          cur.append(ch);
        }
      } else if (ch == '"') {
        inQuotes = true;
      } else if (ch == ',') {
        field.add(cur.toString());
        cur.setLength(0);
      } else if (ch == '\n' || ch == '\r') {
        if (cur.length() > 0 || !field.isEmpty()) {
          field.add(cur.toString());
          cur.setLength(0);
          records.add(field);
          field = new ArrayList<String>();
        }
      } else {
        cur.append(ch);
      }
    }
    if (cur.length() > 0 || !field.isEmpty()) {
      field.add(cur.toString());
      records.add(field);
    }
    return records;
  }
}
