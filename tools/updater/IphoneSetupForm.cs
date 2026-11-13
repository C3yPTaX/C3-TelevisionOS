// C3 Updater – Anleitung "An C3 TV senden" für die iPhone-App "Kurzbefehle".
// Zeigt Adresse und Code zum Kopieren. Fertige Kurzbefehl-Dateien lässt Apple nur signiert zu,
// daher eine kurze Schritt-für-Schritt-Anleitung.

using System;
using System.Drawing;
using System.Windows.Forms;

namespace C3
{
    class IphoneSetupForm : Form
    {
        const int W = 600;
        readonly TextBox codeBox;
        readonly Func<string> regenerate;

        public IphoneSetupForm(string token, string tvIp, Func<string> regenerate)
        {
            this.regenerate = regenerate;
            Text = "iPhone einrichten";
            AutoScaleDimensions = new SizeF(96f, 96f);
            AutoScaleMode = AutoScaleMode.Dpi;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            ShowInTaskbar = false;
            StartPosition = FormStartPosition.CenterParent;
            BackColor = Theme.Bg;
            ForeColor = Theme.Fg;
            Font = Theme.Font(10f, false);

            int y = 20;
            Controls.Add(new Label { Text = "An C3 TV senden", Font = Theme.Font(15f, true), AutoSize = true, Location = new Point(24, y), BackColor = Theme.Bg });
            y += 40;
            Controls.Add(new Label
            {
                Text = "Funktioniert, solange „Privat“ an ist und iPhone und TV im selben WLAN sind.",
                ForeColor = Theme.Muted, AutoSize = false, Size = new Size(W - 48, 22), Location = new Point(24, y), BackColor = Theme.Bg,
            });
            y += 34;

            string ip = string.IsNullOrEmpty(tvIp) ? "<TV-IP>" : tvIp.Split(':')[0];
            y = AddValue("Adresse", "http://c3tv.local:8765/open", y);
            y = AddValue("Adresse (falls c3tv.local nicht klappt)", "http://" + ip + ":8765/open", y);
            y = AddValue("Header-Name", "X-C3-Token", y);
            codeBox = AddValueBox("Code (geheim halten)", token, ref y);

            var steps = new Label
            {
                Text =
                    "1.  App „Kurzbefehle“ öffnen → „+“ → Name: „An C3 TV senden“.\n" +
                    "2.  Unten „i“ (Details) → „Im Share-Sheet anzeigen“ einschalten.\n" +
                    "     Bei „Erhält“ nur „URLs“ und „Safari-Webseiten“ auswählen.\n" +
                    "3.  Aktion „Inhalte von URL abrufen“ hinzufügen:\n" +
                    "     • URL: Adresse von oben einfügen\n" +
                    "     • Methode: POST\n" +
                    "     • Header: Feld hinzufügen → Name „X-C3-Token“, Wert = Code\n" +
                    "     • Anfragetext: JSON → Feld hinzufügen → Text, Schlüssel „url“,\n" +
                    "       Wert = Variable „Kurzbefehleingabe“\n" +
                    "4.  Optional: Aktion „Mitteilung anzeigen“ – „Am TV geöffnet“.\n" +
                    "5.  In Safari: Teilen → „An C3 TV senden“. Beim ersten Mal den Zugriff\n" +
                    "     auf das lokale Netzwerk erlauben.",
                ForeColor = Theme.Fg,
                Font = Theme.Font(9.5f, false),
                AutoSize = false,
                Size = new Size(W - 48, 230),
                Location = new Point(24, y + 6),
                BackColor = Theme.Bg,
            };
            Controls.Add(steps);
            y += 246;

            var renew = new RoundButton { Text = "Neuen Code erzeugen", Location = new Point(24, y), Size = new Size(260, 36) };
            renew.Click += delegate
            {
                if (MessageBox.Show(this, "Der alte Code funktioniert danach nicht mehr. Fortfahren?", "C3 Updater", MessageBoxButtons.YesNo) != DialogResult.Yes) return;
                codeBox.Text = this.regenerate();
                MessageBox.Show(this, "Neuer Code erzeugt. Im Updater „Privat an“ klicken und den Code im Kurzbefehl ersetzen.", "C3 Updater");
            };
            var close = new RoundButton { Text = "Fertig", Primary = true, Location = new Point(W - 24 - 160, y), Size = new Size(160, 36) };
            close.Click += delegate { Close(); };
            Controls.Add(renew);
            Controls.Add(close);
            AcceptButton = close;
            y += 56;

            ClientSize = new Size(W, y);
        }

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            Theme.DarkTitleBar(Handle);
        }

        int AddValue(string label, string value, int y)
        {
            AddValueBox(label, value, ref y);
            return y;
        }

        TextBox AddValueBox(string label, string value, ref int y)
        {
            Controls.Add(new Label { Text = label, ForeColor = Theme.Muted, Font = Theme.Font(9f, false), AutoSize = true, Location = new Point(24, y), BackColor = Theme.Bg });
            var field = new Field("") { Location = new Point(24, y + 20), Size = new Size(W - 48 - 110, 34) };
            field.Box.Text = value;
            field.Box.ReadOnly = true;
            field.Box.BackColor = Theme.Field;
            var copy = new RoundButton { Text = "Kopieren", Location = new Point(W - 24 - 100, y + 20), Size = new Size(100, 34) };
            copy.Click += delegate
            {
                try { Clipboard.SetText(field.Box.Text); copy.Text = "Kopiert"; } catch { }
            };
            Controls.Add(field);
            Controls.Add(copy);
            y += 64;
            return field.Box;
        }
    }
}
