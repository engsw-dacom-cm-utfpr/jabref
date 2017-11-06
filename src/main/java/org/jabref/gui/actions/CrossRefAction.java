package org.jabref.gui.actions;

import javafx.collections.ObservableList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jabref.gui.BasePanel;
import org.jabref.gui.JabRefFrame;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;

import javax.swing.Action;
import javax.swing.JTabbedPane;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CrossRefAction extends MnemonicAwareAction {
  private static final Log LOGGER = LogFactory.getLog(NewEntryAction.class);

  private final JabRefFrame jabRefFrame;

  public CrossRefAction(JabRefFrame jabRefFrame) {
    this.jabRefFrame = jabRefFrame;
    putValue(Action.NAME, Localization.menuTitle("Generate cross-references") + "...");
    putValue(Action.SHORT_DESCRIPTION, Localization.lang("New BibTeX entry"));
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    JTabbedPane tabbedPane = jabRefFrame.getTabbedPane();

    if (tabbedPane.getTabCount() > 0) {
      try {
        BasePanel panel = (BasePanel) tabbedPane.getSelectedComponent();
        BibDatabaseContext context = panel.getBibDatabaseContext();
        ObservableList<BibEntry> entries = context.getDatabase().getEntries();

        System.out.println("Got " + entries.size() + " entries");

        for (BibEntry entry : entries) {
          if (entry.getType().equals("article")) {
            Optional<String> author = entry.getField("author");
            Optional<String> bookTitle = entry.getField("booktitle");
            Optional<String> title = entry.getField("title");
            Optional<String> year = entry.getField("year");

            entry.clearField("booktitle");
            // gera proceeding
            // coloca booktitle
            System.out.println(entry.getId() + " => " + entry.getType());
          }
        }
      } catch (Throwable ex) {
        LOGGER.error("Problem with generating cross-references...", ex);
      }
    }
  }

  private List<BibEntry> findRelatives(BibEntry entry, List<BibEntry> entries) {
    List<BibEntry> result = new ArrayList<>();
    String bookTitle = entry.getField("booktitle").orElseThrow(() -> new IllegalArgumentException("BibEntry doesn't have a booktitle field"));

    for (BibEntry e : entries) {
      if (e != entry && e.getType().equals("article")) {
        Optional<String> eBookTitle = e.getField("booktitle");

        if (eBookTitle.isPresent() && isBookTitleRelative(bookTitle, eBookTitle.get())) {
          result.add(e);
        }
      }
    }

    return result;
  }

  private boolean isBookTitleRelative(String bookTitle, String eBookTitle) {
    return false;
  }
}
