/*
 *  MediathekView
 *  Copyright (C) 2008 W. Xaver
 *  W.Xaver[at]googlemail.com
 *  http://zdfmediathk.sourceforge.net/
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package mediathek.update;

import de.mediathekview.mlib.tool.Functions;
import de.mediathekview.mlib.tool.Listener;
import de.mediathekview.mlib.tool.Log;
import de.mediathekview.mlib.tool.SysMsg;
import mediathek.config.Daten;
import mediathek.config.Konstanten;
import mediathek.config.MVConfig;
import mediathek.daten.DatenPset;
import mediathek.daten.ListePset;
import mediathek.daten.ListePsetVorlagen;
import mediathek.gui.dialog.DialogNewSet;
import mediathek.tool.FormatterUtil;
import mediathek.tool.GuiFunktionenProgramme;

import javax.swing.*;
import java.util.Date;

import static java.lang.Thread.sleep;

public class CheckUpdate {

    private static boolean updateCheckAlreadyPerformed = false;
    private final Daten daten;
    private final JFrame parent;

    public CheckUpdate(JFrame parent, Daten daten) {
        this.daten = daten;
        this.parent = parent;
    }

    public void checkProgUpdate() {
        new Thread(this::prog).start();
    }

    private synchronized void prog() {
        try {
            if (!Boolean.parseBoolean(MVConfig.get(MVConfig.Configs.SYSTEM_UPDATE_SUCHEN))) {
                // will der User nicht
                return;
            }

            if (MVConfig.get(MVConfig.Configs.SYSTEM_BUILD_NR).equals(Functions.getProgVersion().toString())
                    && MVConfig.get(MVConfig.Configs.SYSTEM_UPDATE_DATUM).equals(FormatterUtil.FORMATTER_yyyyMMdd.format(new Date()))) {
                // keine neue Version und heute schon gemacht
                return;
            }
            // damit geänderte Sets gleich gemeldet werden und nicht erst morgen
            final ProgrammUpdateSuchen pgrUpdate = new ProgrammUpdateSuchen();
            if (pgrUpdate.checkVersion(false /* bei aktuell anzeigen */, true /* Hinweis */, false /* hinweiseAlleAnzeigen */)) {
                Listener.notify(Listener.EREIGNIS_MEDIATHEKGUI_UPDATE_VERFUEGBAR, CheckUpdate.class.getSimpleName());
            } else {
                Listener.notify(Listener.EREIGNIS_MEDIATHEKGUI_PROGRAMM_AKTUELL, CheckUpdate.class.getSimpleName());
            }

            //==============================================
            // Sets auf Update prüfen
            checkForPsetUpdates();

            try {
                sleep(10_000);
            } catch (InterruptedException ignored) {
            }
            Listener.notify(Listener.EREIGNIS_MEDIATHEKGUI_ORG_TITEL, CheckUpdate.class.getSimpleName());

        } catch (Exception ex) {
            Log.errorLog(794612801, ex);
        }
    }

    private void checkForPsetUpdates() {
        if (updateCheckAlreadyPerformed) {
            return;// nur einmal laufen
        } else
            updateCheckAlreadyPerformed = true;

        try {
            SwingUtilities.invokeLater(() -> {
                ListePset listePsetStandard = ListePsetVorlagen.getStandarset(parent, daten, false /*replaceMuster*/);
                String version = MVConfig.get(MVConfig.Configs.SYSTEM_VERSION_PROGRAMMSET);
                if (listePsetStandard != null) {
                    if (!Daten.listePset.isEmpty()) {
                        // ansonsten ist die Liste leer und dann gibts immer was
                        if (listePsetStandard.version.isEmpty()) {
                            // dann hat das Laden der aktuellen Standardversion nicht geklappt
                            return;
                        }
                        if (/*!Daten.delSets &&*/version.equals(listePsetStandard.version)) {
                            // dann passt alles
                            return;
                        } else {
                            DialogNewSet dialogNewSet = new DialogNewSet(parent);
                            dialogNewSet.setVisible(true);
                            if (!dialogNewSet.ok) {
                                SysMsg.sysMsg("Setanlegen: Abbruch");
                                if (!dialogNewSet.morgen) {
                                    // dann auch die Versionsnummer aktualisieren
                                    SysMsg.sysMsg("Setanlegen: Nicht wieder nachfragen");
                                    MVConfig.add(MVConfig.Configs.SYSTEM_VERSION_PROGRAMMSET, listePsetStandard.version);
                                }
                                SysMsg.sysMsg("==========================================");
                                // dann halt nicht
                                return;
                            }
                        }
                    }

                    //========================================
                    // gibt keine Sets oder aktualisieren
                    // damit die Variablen ersetzt werden
                    ListePset.progMusterErsetzen(parent, listePsetStandard);

                    MVConfig.add(MVConfig.Configs.SYSTEM_VERSION_PROGRAMMSET, listePsetStandard.version);
                    // die Zielpafade anpassen
                    ListePset listePsetOrgSpeichern = Daten.listePset.getListeSpeichern();
                    if (!listePsetOrgSpeichern.isEmpty()) {
                        for (DatenPset psNew : listePsetStandard.getListeSpeichern()) {
                            psNew.arr[DatenPset.PROGRAMMSET_ZIEL_PFAD] = listePsetOrgSpeichern.get(0).arr[DatenPset.PROGRAMMSET_ZIEL_PFAD];
                            psNew.arr[DatenPset.PROGRAMMSET_THEMA_ANLEGEN] = listePsetOrgSpeichern.get(0).arr[DatenPset.PROGRAMMSET_THEMA_ANLEGEN];
                            psNew.arr[DatenPset.PROGRAMMSET_LAENGE_BESCHRAENKEN] = listePsetOrgSpeichern.get(0).arr[DatenPset.PROGRAMMSET_LAENGE_BESCHRAENKEN];
                            psNew.arr[DatenPset.PROGRAMMSET_LAENGE_FIELD_BESCHRAENKEN] = listePsetOrgSpeichern.get(0).arr[DatenPset.PROGRAMMSET_LAENGE_FIELD_BESCHRAENKEN];
                            psNew.arr[DatenPset.PROGRAMMSET_MAX_LAENGE] = listePsetOrgSpeichern.get(0).arr[DatenPset.PROGRAMMSET_MAX_LAENGE];
                            psNew.arr[DatenPset.PROGRAMMSET_MAX_LAENGE_FIELD] = listePsetOrgSpeichern.get(0).arr[DatenPset.PROGRAMMSET_MAX_LAENGE_FIELD];
                        }
                    }
                    if (!Daten.listePset.isEmpty()) {
                        // wenn leer, dann gibts immer die neuen und die sind dann auch aktiv
                        for (DatenPset psNew : listePsetStandard) {
                            // die bestehenden Sets sollen nicht gestört werden
                            psNew.arr[DatenPset.PROGRAMMSET_IST_ABSPIELEN] = Boolean.FALSE.toString();
                            psNew.arr[DatenPset.PROGRAMMSET_IST_ABO] = Boolean.FALSE.toString();
                            psNew.arr[DatenPset.PROGRAMMSET_IST_BUTTON] = Boolean.FALSE.toString();
                            psNew.arr[DatenPset.PROGRAMMSET_IST_SPEICHERN] = Boolean.FALSE.toString();
                        }
                        // damit man sie auch findet :)
                        String date = FormatterUtil.FORMATTER_ddMMyyyy.format(new Date());
                        listePsetStandard.forEach((psNew) -> psNew.arr[DatenPset.PROGRAMMSET_NAME] = psNew.arr[DatenPset.PROGRAMMSET_NAME] + ", neu: " + date);
                    }
                    GuiFunktionenProgramme.addSetVorlagen(daten.getMediathekGui(), daten, listePsetStandard, true /*auto*/, true /*setVersion*/); // damit auch AddOns geladen werden
                    SysMsg.sysMsg("Setanlegen: OK");
                    SysMsg.sysMsg("==========================================");
                }
            });
        } catch (Exception ignored) {
        }
    }

}
