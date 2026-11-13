// C3 Updater – privater Bereich.
// An:  Silk wieder aktivieren, Seitenliste an C3 senden (Reihe "Privat" erscheint).
// Aus: Liste auf dem TV löschen, alle Silk-Daten löschen (Verlauf, Cookies, Cache) und
//      Silk deaktivieren – sofern Silk eine normale App ist (Systempakete werden nicht deaktiviert).
// Die Seitenliste liegt auf dem PC verschlüsselt (Windows DPAPI, nur dieses Benutzerkonto).

using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace C3
{
    public partial class UpdaterForm
    {
        const string SilkPackage = "com.amazon.cloud9";
        readonly List<Site> sites = new List<Site>();

        class Site
        {
            public string Name;
            public string Url;
            public override string ToString() { return Name + "  –  " + Url; }
        }

        string PrivatePath()
        {
            return Path.Combine(Path.GetDirectoryName(devicesPath), "private.dat");
        }

        void LoadSites()
        {
            sites.Clear();
            try
            {
                if (File.Exists(PrivatePath()))
                {
                    byte[] plain = ProtectedData.Unprotect(File.ReadAllBytes(PrivatePath()), null, DataProtectionScope.CurrentUser);
                    foreach (string line in Encoding.UTF8.GetString(plain).Split('\n'))
                    {
                        int sep = line.IndexOf('|');
                        if (sep <= 0) continue;
                        sites.Add(new Site { Name = line.Substring(0, sep), Url = line.Substring(sep + 1).Trim() });
                    }
                }
            }
            catch { }
            RefreshSiteBox(sites.Count > 0 ? sites[0] : null);
        }

        void SaveSites()
        {
            var sb = new StringBuilder();
            foreach (Site s in sites) sb.Append(s.Name).Append('|').Append(s.Url).Append('\n');
            try
            {
                byte[] cipher = ProtectedData.Protect(Encoding.UTF8.GetBytes(sb.ToString()), null, DataProtectionScope.CurrentUser);
                File.WriteAllBytes(PrivatePath(), cipher);
            }
            catch { }
        }

        void RefreshSiteBox(Site select)
        {
            siteBox.Items.Clear();
            foreach (Site s in sites) siteBox.Items.Add(s);
            if (select != null) siteBox.SelectedItem = select;
            removeSiteButton.Enabled = sites.Count > 0;
        }

        void AddSite()
        {
            string name = Clean(siteNameField.Box.Text);
            string url = siteUrlField.Box.Text.Trim().Replace("'", "%27").Replace(";", "%3B").Replace("|", "%7C");
            if (url.Length > 0 && !url.StartsWith("http://", StringComparison.OrdinalIgnoreCase) && !url.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
                url = "https://" + url;
            Uri parsed;
            if (name.Length == 0 || !Uri.TryCreate(url, UriKind.Absolute, out parsed) || parsed.Host.Length == 0)
            {
                System.Windows.Forms.MessageBox.Show(this, "Bitte Name und gültige Adresse eintragen.", "C3 Updater");
                return;
            }
            var site = new Site { Name = name, Url = url };
            sites.RemoveAll(delegate (Site s) { return string.Equals(s.Name, name, StringComparison.OrdinalIgnoreCase); });
            sites.Add(site);
            SaveSites();
            RefreshSiteBox(site);
            siteNameField.Box.Text = "";
            siteUrlField.Box.Text = "";
            Status("Seite gespeichert – mit \"Privat an\" an den TV senden.");
        }

        void RemoveSite()
        {
            var s = siteBox.SelectedItem as Site;
            if (s == null) return;
            sites.Remove(s);
            SaveSites();
            RefreshSiteBox(sites.Count > 0 ? sites[0] : null);
            Status("Seite entfernt – mit \"Privat an\" auf dem TV aktualisieren.");
        }

        void DoPrivate(string ip, bool enable)
        {
            string serial = Connect(ip);
            if (serial == null) return;

            bool installed = Adb("-s " + serial + " shell pm list packages " + SilkPackage, 15000).Contains("package:" + SilkPackage);
            bool userApp = Adb("-s " + serial + " shell pm list packages -3 " + SilkPackage, 15000).Contains("package:" + SilkPackage);

            if (enable)
            {
                if (installed)
                {
                    Log(Adb("-s " + serial + " shell pm enable " + SilkPackage, 15000).Trim());
                }
                else
                {
                    Log("Silk ist nicht installiert – öffne Silk im Amazon Appstore auf dem TV.");
                    Adb("-s " + serial + " shell am start -a android.intent.action.VIEW -d 'amzn://apps/android?p=" + SilkPackage + "'", 15000);
                }
                var parts = new List<string>();
                foreach (Site s in sites) parts.Add(s.Name + "|" + s.Url);
                Adb("-s " + serial + " shell am start -n " + MainActivity +
                    " -a de.c3.tv.action.SET_PRIVATE --ez de.c3.tv.extra.ENABLED true --es de.c3.tv.extra.SITES '" +
                    string.Join(";", parts.ToArray()) + "'", 20000);
                Log("Privat an: " + sites.Count + " Seiten gesendet.");
                UI(delegate { SetPrivateState(true); });
                Status(installed ? "Privat ist an." : "Privat ist an – Silk bitte im Appstore installieren.");
                return;
            }

            Adb("-s " + serial + " shell am start -n " + MainActivity +
                " -a de.c3.tv.action.SET_PRIVATE --ez de.c3.tv.extra.ENABLED false", 20000);
            Log("Privat-Reihe auf dem TV entfernt.");
            if (installed)
            {
                Log("Silk-Daten löschen: " + Adb("-s " + serial + " shell pm clear " + SilkPackage, 20000).Trim());
                if (userApp)
                {
                    Log("Silk deaktivieren: " + Adb("-s " + serial + " shell pm disable-user --user 0 " + SilkPackage, 15000).Trim());
                }
                else
                {
                    Log("Silk ist ein Systempaket – Daten gelöscht, Silk bleibt aktiv (Systempakete werden nicht deaktiviert).");
                }
            }
            Adb("-s " + serial + " shell am start -n " + MainActivity, 20000);
            UI(delegate { SetPrivateState(false); });
            Status("Privat ist aus. Silk-Verlauf gelöscht.");
        }

        void SetPrivateState(bool on)
        {
            if (on) privatePill.Set("An", Theme.Ok, Theme.OkBg);
            else privatePill.Set("Aus", Theme.Muted, Theme.Field);
            privatePill.Left = CardW - 16 - privatePill.Width;
            privateOnButton.Selected = on;
            privateOffButton.Selected = !on;
            privateOnButton.Invalidate();
            privateOffButton.Invalidate();
        }
    }
}
