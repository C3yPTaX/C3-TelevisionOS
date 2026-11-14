// C3 Updater – eigene, schlichte Bedienelemente im Stil von C3 Television OS
// (dunkel, abgerundet, ein Akzent). Reines GDI+, keine Abhängigkeiten.

using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace C3
{
    static class Theme
    {
        public static readonly Color Bg = Color.FromArgb(17, 19, 24);
        public static readonly Color Card = Color.FromArgb(26, 29, 36);
        public static readonly Color Field = Color.FromArgb(34, 38, 47);
        public static readonly Color FieldHover = Color.FromArgb(42, 47, 58);
        public static readonly Color Line = Color.FromArgb(38, 255, 255, 255);
        public static readonly Color Fg = Color.FromArgb(242, 244, 247);
        public static readonly Color Muted = Color.FromArgb(150, 156, 170);
        public static readonly Color Accent = Color.FromArgb(90, 169, 255);
        public static readonly Color AccentHover = Color.FromArgb(120, 186, 255);
        public static readonly Color OnAccent = Color.FromArgb(8, 17, 28);
        public static readonly Color Ok = Color.FromArgb(142, 224, 168);
        public static readonly Color OkBg = Color.FromArgb(36, 52, 199, 123);
        public static readonly Color Warn = Color.FromArgb(255, 211, 138);
        public static readonly Color WarnBg = Color.FromArgb(36, 245, 165, 36);

        public static Font Font(float size, bool semibold)
        {
            string[] families = semibold
                ? new[] { "Segoe UI Variable Display Semib", "Segoe UI Semibold", "Segoe UI" }
                : new[] { "Segoe UI Variable Text", "Segoe UI" };
            foreach (string f in families)
            {
                using (var probe = new Font(f, size))
                {
                    if (string.Equals(probe.Name, f, StringComparison.OrdinalIgnoreCase))
                        return new Font(f, size, semibold && f == "Segoe UI" ? FontStyle.Bold : FontStyle.Regular);
                }
            }
            return new Font(FontFamily.GenericSansSerif, size);
        }

        public static GraphicsPath Rounded(Rectangle r, int radius)
        {
            int d = Math.Min(radius * 2, Math.Min(r.Width, r.Height));
            var p = new GraphicsPath();
            if (d <= 0) { p.AddRectangle(r); return p; }
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        [DllImport("dwmapi.dll")]
        static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);

        /// Dark title bar (Windows 10 20H1+ / 11) and caption colour (Windows 11).
        public static void DarkTitleBar(IntPtr handle)
        {
            try
            {
                int on = 1;
                if (DwmSetWindowAttribute(handle, 20, ref on, 4) != 0) DwmSetWindowAttribute(handle, 19, ref on, 4);
                int caption = Bg.R | (Bg.G << 8) | (Bg.B << 16);
                DwmSetWindowAttribute(handle, 35, ref caption, 4);
            }
            catch { }
        }
    }

    /// Rounded panel ("Karte").
    class Card : Panel
    {
        public int Radius = 14;
        public Color Fill = Theme.Card;

        public Card()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            BackColor = Theme.Bg;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var r = new Rectangle(0, 0, Width - 1, Height - 1);
            using (var path = Theme.Rounded(r, Radius))
            using (var fill = new SolidBrush(Fill))
            using (var line = new Pen(Theme.Line))
            {
                e.Graphics.FillPath(fill, path);
                e.Graphics.DrawPath(line, path);
            }
        }
    }

    /// Flat rounded button: primary (accent), secondary (field) or selected (segment).
    class RoundButton : Button
    {
        public bool Primary;
        public bool Selected;
        public bool Light;
        public int Radius = 10;
        bool hover;
        bool down;

        public RoundButton()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            FlatStyle = FlatStyle.Flat;
            FlatAppearance.BorderSize = 0;
            Cursor = Cursors.Hand;
            Font = Theme.Font(10f, true);
        }

        protected override void OnMouseEnter(EventArgs e) { hover = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { hover = false; down = false; Invalidate(); base.OnMouseLeave(e); }
        protected override void OnMouseDown(MouseEventArgs e) { down = true; Invalidate(); base.OnMouseDown(e); }
        protected override void OnMouseUp(MouseEventArgs e) { down = false; Invalidate(); base.OnMouseUp(e); }

        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Parent != null ? (Parent is Card ? ((Card)Parent).Fill : Parent.BackColor) : Theme.Bg);

            Color fill, text;
            if (Primary || Selected)
            {
                fill = hover ? Theme.AccentHover : Theme.Accent;
                text = Theme.OnAccent;
            }
            else if (Light)
            {
                fill = hover ? Color.White : Theme.Fg;
                text = Theme.Bg;
            }
            else
            {
                fill = hover ? Theme.FieldHover : Theme.Field;
                text = Theme.Fg;
            }
            if (!Enabled)
            {
                fill = Color.FromArgb(120, fill);
                text = Color.FromArgb(140, text);
            }

            var r = new Rectangle(0, 0, Width - 1, Height - 1);
            if (down) r.Inflate(-1, -1);
            using (var path = Theme.Rounded(r, Radius))
            using (var brush = new SolidBrush(fill))
            {
                g.FillPath(brush, path);
            }
            TextRenderer.DrawText(g, Text, Font, r, text,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
            if (Focused && ShowFocusCues)
            {
                using (var pen = new Pen(Color.FromArgb(160, 255, 255, 255), 1.5f))
                using (var path = Theme.Rounded(new Rectangle(1, 1, Width - 3, Height - 3), Radius))
                    g.DrawPath(pen, path);
            }
        }
    }

    /// Small status capsule (e.g. "TV verbunden · 0.4.0").
    class Pill : Label
    {
        public Color Fill = Theme.Field;

        public Pill()
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
            AutoSize = false;
            Font = Theme.Font(8.5f, true);
            TextAlign = ContentAlignment.MiddleCenter;
        }

        public void Set(string text, Color fg, Color bg)
        {
            Text = text;
            ForeColor = fg;
            Fill = bg;
            using (var g = CreateGraphics())
            {
                var size = TextRenderer.MeasureText(g, text, Font);
                Width = size.Width + 22;
            }
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            e.Graphics.Clear(Parent != null ? (Parent is Card ? ((Card)Parent).Fill : Parent.BackColor) : Theme.Bg);
            using (var path = Theme.Rounded(new Rectangle(0, 0, Width - 1, Height - 1), Height / 2))
            using (var brush = new SolidBrush(Fill))
                e.Graphics.FillPath(brush, path);
            TextRenderer.DrawText(e.Graphics, Text, Font, ClientRectangle, ForeColor,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
        }
    }

    /// Rounded dark field hosting a borderless TextBox.
    /// Rounded text field with a grey hint ("Platzhalter") that disappears on click/focus.
    class Field : Panel
    {
        public readonly TextBox Box = new TextBox();
        readonly Label hint = new Label();

        public Field(string cue)
        {
            SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            Height = 34;
            Box.BorderStyle = BorderStyle.None;
            Box.BackColor = Theme.Field;
            Box.ForeColor = Theme.Fg;
            Box.Font = Theme.Font(10f, false);

            // The text box covers the panel, so the hint is a label on top of it.
            hint.AutoSize = false;
            hint.BackColor = Theme.Field;
            hint.ForeColor = Theme.Muted;
            hint.Font = Box.Font;
            hint.TextAlign = ContentAlignment.MiddleLeft;
            hint.AutoEllipsis = true;
            hint.Cursor = Cursors.IBeam;
            hint.Click += delegate { Box.Focus(); };

            Controls.Add(hint);
            Controls.Add(Box);
            hint.BringToFront();
            Cue = cue;
            Box.GotFocus += delegate { UpdateHint(); Invalidate(); };
            Box.LostFocus += delegate { UpdateHint(); Invalidate(); };
            Box.TextChanged += delegate { UpdateHint(); };
            EnabledChanged += delegate { UpdateHint(); };
        }

        public string Cue
        {
            get { return hint.Text; }
            set { hint.Text = value ?? ""; UpdateHint(); }
        }

        void UpdateHint()
        {
            hint.Visible = Box.Text.Length == 0 && !Box.Focused && hint.Text.Length > 0;
        }

        protected override void OnLayout(LayoutEventArgs e)
        {
            base.OnLayout(e);
            Box.Location = new Point(12, (Height - Box.PreferredHeight) / 2);
            Box.Width = Width - 24;
            hint.Location = new Point(12, 2);
            hint.Size = new Size(Width - 24, Height - 4);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            e.Graphics.Clear(Parent is Card ? ((Card)Parent).Fill : Theme.Bg);
            using (var path = Theme.Rounded(new Rectangle(0, 0, Width - 1, Height - 1), 10))
            using (var brush = new SolidBrush(Theme.Field))
            using (var pen = new Pen(Box.Focused ? Theme.Accent : Theme.Line))
            {
                e.Graphics.FillPath(brush, path);
                e.Graphics.DrawPath(pen, path);
            }
        }
    }

    /// Dark drop-down list (owner drawn).
    class DarkCombo : ComboBox
    {
        public DarkCombo()
        {
            DropDownStyle = ComboBoxStyle.DropDownList;
            DrawMode = DrawMode.OwnerDrawFixed;
            FlatStyle = FlatStyle.Flat;
            BackColor = Theme.Field;
            ForeColor = Theme.Fg;
            Font = Theme.Font(10f, false);
            ItemHeight = 26;
        }

        protected override void OnDrawItem(DrawItemEventArgs e)
        {
            bool selected = (e.State & DrawItemState.Selected) != 0 && (e.State & DrawItemState.ComboBoxEdit) == 0;
            using (var bg = new SolidBrush(selected ? Theme.FieldHover : Theme.Field))
                e.Graphics.FillRectangle(bg, e.Bounds);
            string text = e.Index >= 0 ? Items[e.Index].ToString() : "";
            if (e.Index < 0 && Items.Count == 0) text = "Noch keine Einträge";
            TextRenderer.DrawText(e.Graphics, text, Font, new Rectangle(e.Bounds.X + 8, e.Bounds.Y, e.Bounds.Width - 8, e.Bounds.Height),
                e.Index < 0 ? Theme.Muted : Theme.Fg, TextFormatFlags.VerticalCenter | TextFormatFlags.Left | TextFormatFlags.EndEllipsis);
        }
    }
}
