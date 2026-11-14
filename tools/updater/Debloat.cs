// C3 Updater – Amazon entrümpeln.
// Schaltet Werbung, Datensammlung und ungenutzte Amazon-Dienste per
//   adb shell pm disable-user --user 0 <paket>
// ab (kein Root, nichts wird gelöscht). "Rückgängig" schaltet jedes Paket dieser Listen, das
// gerade abgeschaltet ist, mit "pm enable" wieder ein – das funktioniert auch, wenn die
// Oberfläche am TV hängen sollte, weil es nur ADB vom PC braucht.
// Letzter Ausweg: Werkseinstellungen am TV setzen alle Pakete wieder auf "ein".
//
// Auswahl: Community-Listen (u. a. JLCareglio-Gist, dumbtv.org, XDA-Liste Fire TV Cube 3,
// cleanyourtv) – ohne Pakete, die C3 braucht oder die laut Quellen etwas zerstören.
// Für Fire OS 8 auf diesem TV ungetestet: jedes Paket wird einzeln versucht und protokolliert.

using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;
using System.Windows.Forms;

namespace C3
{
    public partial class UpdaterForm
    {
        /// Werbung, Datensammlung und Amazon-Apps ohne Nutzen für C3 – keine sichtbare Nebenwirkung erwartet.
        static readonly string[][] DebloatBasic =
        {
            new[] { "com.amazon.tv.acr", "Inhaltserkennung (ACR) – was auf dem Bildschirm läuft" },
            new[] { "com.amazon.aria", "Werbe-/Analysedienst" },
            new[] { "com.amazon.aca", "Werbe-/Analysedienst" },
            new[] { "com.amazon.device.crashmanager", "Absturzberichte an Amazon" },
            new[] { "com.amazon.dp.logger", "Protokolle" },
            new[] { "com.amazon.logan", "Protokoll-Upload" },
            new[] { "com.amazon.wirelessmetrics.service", "WLAN-Messdaten" },
            new[] { "com.amazon.connectivitydiag", "Netzwerkdiagnose für Amazon" },
            new[] { "com.amazon.shoptv.client", "Shopping" },
            new[] { "com.amazon.shoptv.firetv.client", "Shopping" },
            new[] { "com.amazon.ftvads.deeplinking", "Werbe-Links" },
            new[] { "com.amazon.device.sale.service", "Angebote" },
            new[] { "com.amazon.imdb.tv.android.app", "Freevee / IMDb TV" },
            new[] { "com.amazon.minitv.android.app", "miniTV" },
            new[] { "com.amazon.gamehub", "Spiele-Hub" },
            new[] { "com.amazon.ods.kindleconnect", "Kindle" },
            new[] { "com.amazon.tahoe", "Amazon Kids" },
        };

        /// Weitere Dienste mit möglicher Nebenwirkung (in Klammern).
        static readonly string[][] DebloatExtended =
        {
            new[] { "com.amazon.bueller.photos", "Amazon Fotos (Fotos im Bildschirmschoner)" },
            new[] { "com.amazon.bueller.music", "Amazon Music" },
            new[] { "com.amazon.audiohome", "Audio-Startseite" },
            new[] { "com.amazon.whisperplay.contracts", "Casting (Fire-TV-App am Handy, „Auf TV abspielen“)" },
            new[] { "com.amazon.whisperplay.service.install", "Installieren über die Fire-TV-App am Handy" },
            new[] { "com.amazon.communication.discovery", "Gerätesuche im Netz (Casting)" },
            new[] { "com.amazon.hedwig", "Benachrichtigungen" },
            new[] { "com.amazon.client.metrics.api", "Nutzungsmessung" },
            new[] { "com.amazon.wifilocker", "WLAN-Passwörter in der Amazon-Cloud" },
            new[] { "com.amazon.tv.ottssocompanionapp", "Anmeldung in Apps über das Amazon-Konto" },
            new[] { "com.amazon.ftv.profilepicker", "Profilauswahl beim Start" },
        };

        /// Wird nie abgeschaltet – auch wenn es versehentlich in einer Liste landet.
        static readonly HashSet<string> DebloatProtected = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "com.amazon.tv.launcher",          // Amazon-Home: Grundlage der Home-Umleitung
            "com.amazon.firehomestarter",
            "com.amazon.tv.settings.v2", "com.amazon.device.settings", "com.amazon.tv.oobe",
            "com.amazon.venezia", "com.amazon.android.marketplace", // Appstore
            "com.amazon.imp",                  // zerstörte laut Quelle Home
            "com.amazon.dcp", "com.amazon.fireinputdevices", "com.amazon.webview.chromium",
            "com.amazon.tv.ime", "com.amazon.tv.inputpreference.service",
            "com.amazon.tv.quicksettings", "com.amazon.tv.quicksettings.ui",
            "com.amazon.providers.contentsupport", "com.amazon.kindleautomatictimezone",
            "com.amazon.device.software.ota", "com.amazon.device.software.ota.override",
            "com.amazon.tv.forcedotaupdater.v2", "com.amazon.tv.easyupgrade",
            "com.amazon.firebat", "com.amazon.avod", "com.netflix.ninja", // Favoriten
            "com.amazon.cloud9",               // Silk für "Privat"
            "de.c3.tv",
        };

        readonly CheckBox debloatExtendedCheck = new CheckBox();
        readonly Pill debloatPill = new Pill();
        readonly RoundButton debloatButton = new RoundButton();
        readonly RoundButton restoreButton = new RoundButton();

        List<string[]> DebloatSelection(bool extended)
        {
            var list = new List<string[]>(DebloatBasic);
            if (extended) list.AddRange(DebloatExtended);
            return list.FindAll(delegate (string[] p) { return !DebloatProtected.Contains(p[0]); });
        }

        HashSet<string> PackageSet(string serial, string flags)
        {
            var set = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            string output = Adb("-s " + serial + " shell pm list packages " + flags, 30000);
            foreach (string line in output.Split('\n'))
            {
                string t = line.Trim();
                if (t.StartsWith("package:")) set.Add(t.Substring(8));
            }
            return set;
        }

        int MemAvailableMb(string serial)
        {
            string m = Adb("-s " + serial + " shell cat /proc/meminfo", 15000);
            var match = Regex.Match(m, @"MemAvailable:\s+(\d+)\s*kB");
            return match.Success ? int.Parse(match.Groups[1].Value) / 1024 : -1;
        }

        void DoDebloat(string ip, bool extended)
        {
            string serial = Connect(ip);
            if (serial == null) return;

            HashSet<string> installed = PackageSet(serial, "");
            HashSet<string> disabled = PackageSet(serial, "-d");
            int before = MemAvailableMb(serial);
            Log("Arbeitsspeicher frei vorher: " + before + " MB");

            int done = 0, already = 0, missing = 0, failed = 0;
            foreach (string[] p in DebloatSelection(extended))
            {
                string pkg = p[0];
                if (!installed.Contains(pkg)) { missing++; Log("–  " + pkg + "  (nicht vorhanden)"); continue; }
                if (disabled.Contains(pkg)) { already++; Log("✓  " + pkg + "  (war schon aus)"); continue; }
                Status("Schalte ab: " + p[1] + " …");
                string r = Adb("-s " + serial + " shell pm disable-user --user 0 " + pkg, 20000).Trim();
                if (r.IndexOf("disabled", StringComparison.OrdinalIgnoreCase) >= 0 && r.IndexOf("Exception", StringComparison.OrdinalIgnoreCase) < 0)
                {
                    done++;
                    Log("✓  " + pkg + "  – " + p[1]);
                }
                else
                {
                    failed++;
                    Log("✗  " + pkg + "  – vom System gesperrt: " + r);
                }
            }

            // C3 wieder nach vorne und prüfen, dass Amazon-Home weiter läuft.
            Adb("-s " + serial + " shell am start -n " + MainActivity, 20000);
            int after = MemAvailableMb(serial);
            Log("Arbeitsspeicher frei nachher: " + after + " MB" + (before > 0 && after > 0 ? " (" + (after - before >= 0 ? "+" : "") + (after - before) + " MB)" : ""));
            Log(done + " abgeschaltet, " + already + " waren schon aus, " + failed + " gesperrt, " + missing + " nicht vorhanden.");
            RefreshDebloatState(serial);
            Status("Entrümpelt: " + (done + already) + " Amazon-Dienste aus" + (failed > 0 ? ", " + failed + " gesperrt" : "") + ". Rückgängig jederzeit möglich.");
        }

        void DoRestore(string ip)
        {
            string serial = Connect(ip);
            if (serial == null) return;

            HashSet<string> disabled = PackageSet(serial, "-d");
            int restored = 0;
            foreach (string[] p in DebloatSelection(true))
            {
                if (!disabled.Contains(p[0])) continue;
                Status("Schalte ein: " + p[1] + " …");
                Log(Adb("-s " + serial + " shell pm enable " + p[0], 20000).Trim());
                restored++;
            }
            RefreshDebloatState(serial);
            Status(restored == 0 ? "Nichts abgeschaltet – alles war schon an." : restored + " Amazon-Dienste wieder eingeschaltet.");
        }

        /// Pill: wie viele Pakete der Listen gerade aus sind.
        void RefreshDebloatState(string serial)
        {
            HashSet<string> disabled = PackageSet(serial, "-d");
            int off = 0;
            foreach (string[] p in DebloatSelection(true)) if (disabled.Contains(p[0])) off++;
            UI(delegate
            {
                if (off == 0) debloatPill.Set("–", Theme.Muted, Theme.Field);
                else debloatPill.Set(off + " aus", Theme.Ok, Theme.OkBg);
            });
        }
    }
}
