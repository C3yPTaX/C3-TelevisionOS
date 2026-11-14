// C3 Updater – Oberfläche im Stil von C3 Television OS.
// Zwei Spalten, damit das Fenster auf Laptop-Bildschirme passt:
//   links  Fernseher · Home-Taste · C3 Desktop (PCs)
//   rechts Privat · Dieser PC (Sunshine)
// Darunter Status und einklappbares Protokoll über die volle Breite.

using System;
using System.Drawing;
using System.Windows.Forms;

namespace C3
{
    public partial class UpdaterForm : Form
    {
        const int CardW = 572;
        const int Gutter = 24;
        const int ColGap = 20;
        const int LeftX = Gutter;
        const int RightX = Gutter + CardW + ColGap;
        const int W = RightX + CardW + Gutter;

        readonly Pill tvPill = new Pill();
        readonly DarkCombo deviceBox = new DarkCombo();
        readonly RoundButton removeButton = new RoundButton();
        readonly Field nameField = new Field("Name, z. B. Wohnzimmer");
        readonly Field ipField = new Field("IP des TV, z. B. 192.168.178.23");
        readonly RoundButton saveButton = new RoundButton();
        readonly RoundButton updateButton = new RoundButton();
        readonly RoundButton startButton = new RoundButton();
        readonly RoundButton redirectOnButton = new RoundButton();
        readonly RoundButton redirectOffButton = new RoundButton();
        readonly DarkCombo pcBox = new DarkCombo();
        readonly RoundButton addPcButton = new RoundButton();
        readonly RoundButton removePcButton = new RoundButton();
        readonly Pill privatePill = new Pill();
        readonly DarkCombo siteBox = new DarkCombo();
        readonly RoundButton removeSiteButton = new RoundButton();
        readonly Field siteNameField = new Field("Name, z. B. Nachrichten");
        readonly Field siteUrlField = new Field("Adresse, z. B. https://…");
        readonly RoundButton addSiteButton = new RoundButton();
        readonly RoundButton privateOnButton = new RoundButton();
        readonly RoundButton privateOffButton = new RoundButton();
        readonly RoundButton iphoneButton = new RoundButton();
        readonly Pill sunshinePill = new Pill();
        readonly Label sysValues = new Label();
        readonly RoundButton sunshineButton = new RoundButton();
        readonly Label statusLabel = new Label();
        readonly LinkLabel detailsLink = new LinkLabel();
        readonly LinkLabel selfUpdateLink = new LinkLabel();
        readonly CheckBox devCheck = new CheckBox();
        readonly TextBox log = new TextBox();

        TextBox nameBox { get { return nameField.Box; } }
        TextBox ipBox { get { return ipField.Box; } }

        int collapsedHeight;

        public UpdaterForm()
        {
            InitStorage();

            Text = "C3 Updater";
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            AutoScaleDimensions = new SizeF(96f, 96f);
            AutoScaleMode = AutoScaleMode.Dpi;
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Theme.Bg;
            ForeColor = Theme.Fg;
            Font = Theme.Font(10f, false);

            int y = 20;

            // ---------------------------------------------------------------- header
            var logo = new PictureBox { Location = new Point(Gutter, y), Size = new Size(44, 44), SizeMode = PictureBoxSizeMode.Zoom, BackColor = Theme.Bg };
            try { logo.Image = new Icon(Icon, 48, 48).ToBitmap(); } catch { }
            var title = new Label { Text = "C3 Television OS", Font = Theme.Font(16f, true), ForeColor = Theme.Fg, AutoSize = true, Location = new Point(Gutter + 56, y - 2), BackColor = Theme.Bg };
            var subtitle = new Label { Text = "Updater · v" + UpdaterVersion, Font = Theme.Font(9.5f, false), ForeColor = Theme.Muted, AutoSize = true, Location = new Point(Gutter + 58, y + 28), BackColor = Theme.Bg };
            tvPill.Height = 26;
            tvPill.Location = new Point(W - Gutter - 150, y + 10);
            tvPill.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            Controls.AddRange(new Control[] { logo, title, subtitle, tvPill });
            SetTvStatus(false, null);
            y += 64;
            int top = y;

            // ================================================================ linke Spalte
            // ---------------------------------------------------------------- Fernseher
            var tv = NewCard(LeftX, y, 182);
            tv.Controls.Add(CardLabel("Fernseher", 16, 12));
            Place(tv, deviceBox, 16, 40, 400, 30);
            deviceBox.SelectedIndexChanged += delegate { OnDeviceSelected(); };
            Setup(removeButton, "Entfernen", false);
            Place(tv, removeButton, 424, 40, 132, 30);
            removeButton.Click += delegate { RemoveDevice(); };
            Place(tv, nameField, 16, 82, 180, 34);
            Place(tv, ipField, 204, 82, 212, 34);
            Setup(saveButton, "Gerät speichern", false);
            Place(tv, saveButton, 424, 82, 132, 34);
            saveButton.Click += delegate { SaveDevice(); };
            Setup(updateButton, "TV aktualisieren", true);
            Place(tv, updateButton, 16, 130, 268, 38);
            updateButton.Click += delegate { RunAsync(Job.Update); };
            Setup(startButton, "C3 starten", false);
            Place(tv, startButton, 292, 130, 264, 38);
            startButton.Click += delegate { RunAsync(Job.Start); };
            y += 182 + 14;

            // ---------------------------------------------------------------- Home-Taste
            var home = NewCard(LeftX, y, 70);
            home.Controls.Add(CardLabel("Home-Taste", 16, 14, Theme.Fg));
            home.Controls.Add(new Label { Text = "2× Home führt jederzeit zu Amazon", ForeColor = Theme.Muted, Font = Theme.Font(9f, false), AutoSize = true, Location = new Point(16, 38), BackColor = Theme.Card });
            Setup(redirectOnButton, "C3", false);
            redirectOnButton.Radius = 15;
            Place(home, redirectOnButton, 376, 20, 88, 30);
            redirectOnButton.Click += delegate { RunAsync(Job.RedirectOn); };
            Setup(redirectOffButton, "Amazon", false);
            redirectOffButton.Radius = 15;
            Place(home, redirectOffButton, 468, 20, 88, 30);
            redirectOffButton.Click += delegate { RunAsync(Job.RedirectOff); };
            y += 70 + 14;

            // ---------------------------------------------------------------- PCs
            var pcCard = NewCard(LeftX, y, 132);
            pcCard.Controls.Add(CardLabel("C3 Desktop · PCs", 16, 12));
            Place(pcCard, pcBox, 16, 40, 400, 30);
            Setup(removePcButton, "Entfernen", false);
            Place(pcCard, removePcButton, 424, 40, 132, 30);
            removePcButton.Click += delegate { pcToRemove = pcBox.SelectedItem as Pc; RunAsync(Job.RemovePc); };
            Setup(addPcButton, "+  Diesen PC hinzufügen", false);
            Place(pcCard, addPcButton, 16, 82, 540, 36);
            addPcButton.Click += delegate { RunAsync(Job.AddPc); };
            y += 132;
            int leftBottom = y;

            // ================================================================ rechte Spalte
            y = top;
            // ---------------------------------------------------------------- Privat
            var priv = NewCard(RightX, y, 220);
            priv.Controls.Add(CardLabel("Privat", 16, 12));
            privatePill.Height = 22;
            privatePill.Location = new Point(CardW - 16 - 60, 12);
            priv.Controls.Add(privatePill);
            privatePill.Set("–", Theme.Muted, Theme.Field);
            Place(priv, siteBox, 16, 40, 400, 30);
            Setup(removeSiteButton, "Entfernen", false);
            Place(priv, removeSiteButton, 424, 40, 132, 30);
            removeSiteButton.Click += delegate { RemoveSite(); };
            Place(priv, siteNameField, 16, 80, 180, 34);
            Place(priv, siteUrlField, 204, 80, 212, 34);
            Setup(addSiteButton, "Hinzufügen", false);
            Place(priv, addSiteButton, 424, 80, 132, 34);
            addSiteButton.Click += delegate { AddSite(); };
            Setup(privateOnButton, "Privat an", false);
            Place(priv, privateOnButton, 16, 126, 268, 36);
            privateOnButton.Click += delegate { RunAsync(Job.PrivateOn); };
            Setup(privateOffButton, "Privat aus + Silk-Verlauf löschen", false);
            Place(priv, privateOffButton, 292, 126, 264, 36);
            privateOffButton.Click += delegate { RunAsync(Job.PrivateOff); };
            Setup(iphoneButton, "iPhone einrichten – Links ans TV senden", false);
            Place(priv, iphoneButton, 16, 170, 540, 36);
            iphoneButton.Click += delegate { ShowIphoneSetup(); };
            y += 220 + 14;

            // ---------------------------------------------------------------- Sunshine
            var sun = NewCard(RightX, y, 186);
            sun.Controls.Add(CardLabel("Dieser PC · Sunshine", 16, 12));
            sunshinePill.Height = 22;
            sunshinePill.Location = new Point(CardW - 16 - 120, 12);
            sun.Controls.Add(sunshinePill);
            sunshinePill.Set("Prüfe …", Theme.Muted, Theme.Field);
            var sysKeys = new Label { Text = "System\nGrafik\nNetzwerk\nSunshine", ForeColor = Theme.Muted, Font = Theme.Font(9.5f, false), Location = new Point(16, 44), Size = new Size(100, 84), BackColor = Theme.Card };
            sysValues.Text = "…\n…\n…\n…";
            sysValues.ForeColor = Theme.Fg;
            sysValues.Font = Theme.Font(9.5f, false);
            sysValues.BackColor = Theme.Card;
            sysValues.AutoEllipsis = true;
            Place(sun, sysKeys, 16, 44, 100, 84);
            Place(sun, sysValues, 120, 44, 436, 84);
            Setup(sunshineButton, "Sunshine installieren", false);
            sunshineButton.Light = true;
            sunshineButton.Enabled = false;
            Place(sun, sunshineButton, 16, 136, 540, 36);
            sunshineButton.Click += delegate { OnSunshineButton(); };
            y += 186;
            y = Math.Max(y, leftBottom) + 14;

            // ---------------------------------------------------------------- footer + log
            statusLabel.Location = new Point(Gutter + 2, y);
            statusLabel.Size = new Size(440, 22);
            statusLabel.ForeColor = Theme.Muted;
            statusLabel.Font = Theme.Font(9.5f, false);
            statusLabel.AutoEllipsis = true;
            statusLabel.Text = "Bereit.";
            detailsLink.Text = "Details anzeigen";
            detailsLink.AutoSize = true;
            detailsLink.LinkColor = Theme.Accent;
            detailsLink.ActiveLinkColor = Theme.AccentHover;
            detailsLink.LinkBehavior = LinkBehavior.HoverUnderline;
            detailsLink.Font = Theme.Font(9.5f, false);
            detailsLink.Location = new Point(W - Gutter - 104, y);
            detailsLink.LinkClicked += delegate { ToggleDetails(); };
            devCheck.Text = "Entwicklerversion";
            devCheck.AutoSize = true;
            devCheck.ForeColor = Theme.Muted;
            devCheck.Font = Theme.Font(9f, false);
            devCheck.Location = new Point(W - Gutter - 104 - 150, y);
            devCheck.Checked = LoadDevChannel();
            useDevChannel = devCheck.Checked;
            devCheck.CheckedChanged += delegate
            {
                useDevChannel = devCheck.Checked;
                SaveDevChannel(useDevChannel);
                ResetSelfUpdateLink();
                CheckSelfUpdateAsync();
            };
            selfUpdateLink.AutoSize = true;
            selfUpdateLink.ActiveLinkColor = Theme.AccentHover;
            selfUpdateLink.LinkBehavior = LinkBehavior.HoverUnderline;
            selfUpdateLink.Font = Theme.Font(9.5f, false);
            selfUpdateLink.Location = new Point(0, y);
            selfUpdateLink.LinkClicked += delegate { OnSelfUpdateClicked(); };
            ResetSelfUpdateLink();
            Controls.Add(selfUpdateLink);
            statusLabel.Size = new Size(W - 2 * Gutter - 460, 22);
            Controls.Add(statusLabel);
            Controls.Add(devCheck);
            Controls.Add(detailsLink);
            y += 30;
            collapsedHeight = y + 8;

            log.Location = new Point(Gutter, y);
            log.Size = new Size(W - 2 * Gutter, 190);
            log.Multiline = true;
            log.ReadOnly = true;
            log.ScrollBars = ScrollBars.Vertical;
            log.BackColor = Theme.Card;
            log.ForeColor = Theme.Fg;
            log.BorderStyle = BorderStyle.None;
            log.Font = new Font("Consolas", 9f);
            log.Visible = false;
            Controls.Add(log);

            AutoScroll = true;
            ApplyHeight(collapsedHeight);
            AcceptButton = updateButton;

            LoadDevices();
            LoadPcs();
            LoadSites();
            DetectSystemAsync();
            CleanupOldExe();
            CheckSelfUpdateAsync();
        }

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            Theme.DarkTitleBar(Handle);
        }

        // ------------------------------------------------------------------ layout helpers

        Card NewCard(int x, int y, int height)
        {
            var c = new Card { Location = new Point(x, y), Size = new Size(CardW, height) };
            Controls.Add(c);
            return c;
        }

        static Label CardLabel(string text, int x, int y, Color? color = null)
        {
            return new Label
            {
                Text = text,
                Font = Theme.Font(10f, true),
                ForeColor = color ?? Theme.Muted,
                AutoSize = true,
                Location = new Point(x, y),
                BackColor = Theme.Card,
            };
        }

        static void Place(Control parent, Control c, int x, int y, int w, int h)
        {
            c.Location = new Point(x, y);
            c.Size = new Size(w, h);
            parent.Controls.Add(c);
        }

        static void Setup(RoundButton b, string text, bool primary)
        {
            b.Text = text;
            b.Primary = primary;
        }

        bool LoadDevChannel()
        {
            try { return System.IO.File.Exists(DevFlagPath()); } catch { return false; }
        }

        void SaveDevChannel(bool on)
        {
            try
            {
                if (on) System.IO.File.WriteAllText(DevFlagPath(), "dev");
                else if (System.IO.File.Exists(DevFlagPath())) System.IO.File.Delete(DevFlagPath());
            }
            catch { }
        }

        string DevFlagPath()
        {
            return System.IO.Path.Combine(System.IO.Path.GetDirectoryName(devicesPath), "channel-dev.txt");
        }

        /// Fits the window on small or scaled screens; content scrolls if it does not fit.
        void ApplyHeight(int desired)
        {
            var area = Screen.FromPoint(Cursor.Position).WorkingArea;
            int maxH = area.Height - 60;
            int maxW = area.Width - 40;
            bool scrollX = W > maxW;
            bool scrollY = desired > maxH;
            int w = scrollX ? maxW : W + (scrollY ? SystemInformation.VerticalScrollBarWidth : 0);
            int h = scrollY ? maxH : desired + (scrollX ? SystemInformation.HorizontalScrollBarHeight : 0);
            AutoScrollMinSize = (scrollX || scrollY) ? new Size(W, desired) : Size.Empty;
            ClientSize = new Size(Math.Min(w, maxW), Math.Min(h, maxH));
        }

        void ResetSelfUpdateLink()
        {
            selfUpdateLink.Text = "Updater aktualisieren";
            selfUpdateLink.LinkColor = Theme.Accent;
            LayoutFooterLinks();
        }

        /// Keeps the updater link left of "Entwicklerversion" and the status text left of it.
        void LayoutFooterLinks()
        {
            selfUpdateLink.Left = devCheck.Left - 20 - selfUpdateLink.PreferredWidth;
            statusLabel.Width = Math.Max(120, selfUpdateLink.Left - 12 - statusLabel.Left);
        }

        void ToggleDetails()
        {
            log.Visible = !log.Visible;
            detailsLink.Text = log.Visible ? "Details ausblenden" : "Details anzeigen";
            ApplyHeight(log.Visible ? collapsedHeight + log.Height + 8 : collapsedHeight);
        }

        // ------------------------------------------------------------------ state shown in the UI

        void SetTvStatus(bool connected, string version)
        {
            if (connected)
                tvPill.Set("TV verbunden" + (string.IsNullOrEmpty(version) ? "" : " · " + version), Theme.Ok, Theme.OkBg);
            else
                tvPill.Set("TV nicht verbunden", Theme.Muted, Theme.Field);
            tvPill.Left = W - Gutter - tvPill.Width;
        }

        void SetRedirectState(bool c3)
        {
            redirectOnButton.Selected = c3;
            redirectOffButton.Selected = !c3;
            redirectOnButton.Invalidate();
            redirectOffButton.Invalidate();
        }

        void SetBusy(bool busy)
        {
            foreach (var b in new Control[] { updateButton, startButton, redirectOnButton, redirectOffButton, addPcButton, removePcButton, pcBox, ipField, nameField, deviceBox, saveButton, privateOnButton, privateOffButton, addSiteButton, siteBox, siteNameField, siteUrlField })
                b.Enabled = !busy;
            removeButton.Enabled = !busy && devices.Count > 0;
            removeSiteButton.Enabled = !busy && sites.Count > 0;
            sunshineButton.Enabled = !busy && sunshineButtonReady;
            Cursor = busy ? Cursors.WaitCursor : Cursors.Default;
        }

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new UpdaterForm());
        }
    }
}
