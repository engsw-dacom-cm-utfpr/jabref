package org.jabref.gui.actions;

import javafx.collections.ObservableList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jabref.gui.BasePanel;
import org.jabref.gui.JabRefFrame;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.FieldName;

import javax.swing.Action;
import javax.swing.JTabbedPane;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public class CrossRefAction extends MnemonicAwareAction {
  private static final Log LOGGER = LogFactory.getLog(CrossRefAction.class);
  private static final String SOURCE_TYPE = "inproceedings";
  private static final String TARGET_TYPE = "proceedings";

  private final JabRefFrame jabRefFrame;

  public CrossRefAction(JabRefFrame jabRefFrame) {
    this.jabRefFrame = jabRefFrame;
    putValue(Action.NAME, Localization.menuTitle("Generate cross-references") + "...");
    putValue(Action.SHORT_DESCRIPTION, Localization.lang("New BibTeX entry"));
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    EventQueue.invokeLater(() -> {
      JTabbedPane tabbedPane = jabRefFrame.getTabbedPane();

      if (tabbedPane.getTabCount() > 0) {
        try {
          BasePanel panel = (BasePanel) tabbedPane.getSelectedComponent();
          BibDatabaseContext context = panel.getBibDatabaseContext();
          ObservableList<BibEntry> entries = context.getDatabase().getEntries();
          Map<BibEntry, List<BibEntry>> mapping = new HashMap<>();

          System.out.println("Iterating entries...");

          for (BibEntry entry : entries) {
            if (entry.getType().equalsIgnoreCase(SOURCE_TYPE)) {
              System.out.println("Finding relatives of [" + entry.getId() + "]");

              List<BibEntry> relatives = findRelatives(entry, entries);

              if (!relatives.isEmpty()) {
                mapping.put(entry, relatives);
                System.out.println("Found " + relatives.size() + " relatives.");
                relatives.forEach(relative -> System.out.println(relative.getId() + " => " + relative.getTitle()));
              }
              else {
                System.out.println("No relatives.");
              }
            }
          }

          System.out.println("Mapping size = " + mapping.size());

          while (!mapping.isEmpty()) {
            Set<BibEntry> related = new HashSet<>();
            BibEntry element = mapping.entrySet().iterator().next().getKey();

            System.out.println("Iterating: " + element.getId() + " => " + element.getTitle());
            related.add(element);
            compoundRelatives(element, mapping, related);
            System.out.println("All related:");
            related.forEach(rel -> System.out.println(rel.getId() + " => " + rel.getTitle()));

            BibEntry conference = buildConference(related);

            System.out.println("Generated " + TARGET_TYPE + "...");
            System.out.println(conference);
            context.getDatabase().insertEntry(conference);
          }
        } catch (Throwable ex) {
          LOGGER.error("Problem with generating cross-references...", ex);
        }
      }
    });
  }

  private BibEntry buildConference(Set<BibEntry> sources) {
    System.out.println("Building " + TARGET_TYPE + " for...");
    sources.forEach(source -> System.out.println(source.getId() + " | " + source.getCiteKeyOptional().orElse(null) + " | " + source.getTitle() + " | " + source.getField(FieldName.YEAR)));

    BibEntry entry = new BibEntry(TARGET_TYPE);
    String[] fields = { FieldName.ADDRESS, FieldName.BOOKTITLE, FieldName.ISBN, FieldName.LOCATION, FieldName.MONTH, FieldName.PUBLISHER, FieldName.YEAR };

    for (String field : fields) {
      Optional<String> value = findField(sources, field);

      value.ifPresent(val -> entry.setField(field, val));
      sources.forEach(source -> source.clearField(field));
      System.out.println("Set [" + field + "] to \"" + value.toString() + "\"");
    }

    Optional<String> foundBookTitle = entry.getField(FieldName.BOOKTITLE);
    Optional<String> foundYear = entry.getField(FieldName.YEAR);
    StringBuilder citeKey = new StringBuilder();
    String citeKeyPart = generateRandomCiteKeyPart();

    citeKey.append("proceedings");
    citeKey.append(":");
    citeKey.append(citeKeyPart);

    if (foundYear.isPresent()) {
      citeKey.append(":");
      citeKey.append(foundYear.get());
    }

    System.out.println("Cite key part=\"" + citeKeyPart + "\"; cite key = \"" + citeKey + "\"");
    foundBookTitle.ifPresent(s -> entry.setField(FieldName.TITLE, s));
    entry.setCiteKey(citeKey.toString());
    entry.setId(citeKeyPart + "_id"); // TODO
    sources.forEach(source -> source.setField(FieldName.CROSSREF, entry.getCiteKeyOptional().orElse("unknown")));
    System.out.println("Crossref=\"" + entry.getCiteKeyOptional().orElse("unknown") + "\"");

    return entry;
  }

  private void compoundRelatives(BibEntry entry, Map<BibEntry, List<BibEntry>> mapping, Set<BibEntry> output) {
    List<BibEntry> relatives = mapping.get(entry);

    if (relatives == null) {
      return;
    }

    output.addAll(relatives);
    mapping.remove(entry);

    for (BibEntry relative : relatives) {
      compoundRelatives(relative, mapping, output);
    }
  }

  private Optional<String> findField(Set<BibEntry> entries, String fieldName) {
    for (BibEntry entry : entries) {
      Optional<String> value = entry.getField(fieldName);

      if (value.isPresent()) {
        return value;
      }
    }

    return Optional.empty();
  }

  private List<BibEntry> findRelatives(BibEntry entry, List<BibEntry> entries) {
    List<BibEntry> result = new ArrayList<>();
    Optional<String> bookTitleOptional = entry.getField(FieldName.BOOKTITLE);

    if (!bookTitleOptional.isPresent()) {
      bookTitleOptional = entry.getField(FieldName.JOURNAL);
    }

    String bookTitle = bookTitleOptional.orElseThrow(() -> new IllegalArgumentException("Entry doesn't have a " + FieldName.BOOKTITLE + " or " + FieldName.JOURNAL + " field"));

    for (BibEntry e : entries) {
      if (e != entry && e.getType().equalsIgnoreCase(SOURCE_TYPE)) {
        Optional<String> eBookTitle = e.getField(FieldName.BOOKTITLE);

        if (eBookTitle.isPresent() && isBookTitleRelative(bookTitle, eBookTitle.get())) {
          result.add(e);
        }
      }
    }

    return result;
  }

  private String generateRandomCiteKeyPart() {
    char[] array = new char[10];
    Random random = new Random();

    for (int i = 0; i < array.length; i++) {
      array[i] = (char) ('a' + random.nextInt(26));
    }

    return new String(array);
  }

  private boolean isBookTitleRelative(String bookTitle, String eBookTitle) {
    String[] words = bookTitle.split(" ");
    int match = 0;

    for (String word : words) {
      if (eBookTitle.contains(word)) {
        match++;
      }
    }

    return match >= words.length / 2;
  }
}
