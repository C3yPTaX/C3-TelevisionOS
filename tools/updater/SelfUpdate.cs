// C3 Updater – aktualisiert sich selbst aus GitHub (gleicher Kanal wie der TV: stabil oder Entwicklerversion).
// Ablauf: neue EXE neben die laufende laden, laufende umbenennen (das erlaubt Windows auch während
// sie läuft), neue an ihren Platz, neu starten. Die alte Datei wird beim nächsten Start gelöscht.

using System;
using System.Diagnostics;
using System.IO;
using System.Threading;
using System.Windows.Forms;

namespace C3
{
    public partial class UpdaterForm
    {
        bool selfUpdateRunning;

        static string ExePath { get { return Application.ExecutablePath; } }

        /// Removes the previous EXE left behind by a self-update (retries while it is still exiting).
        static void CleanupOldExe()
        {
            string old = ExePath + ".old";
            var t = new Thread(delegate ()
            {
                for (int i = 0; i < 20 && File.Exists(old); i++)
                {
                    try { File.Delete(old); } catch { Thread.Sleep(500); }
                }
            });
            t.IsBackground = true;
            t.Start();
        }

        static Version ParseVersion(string text)
        {
            if (string.IsNullOrEmpty(text)) return null;
            string first = text.Trim().Split(' ')[0];
            try { return new Version(first); } catch { return null; }
        }

        string ChannelBase { get { return useDevChannel ? DevBase : StableBase; } }

        /// Looks for a newer updater in the background and highlights the link if there is one.
        void CheckSelfUpdateAsync()
        {
            var t = new Thread(delegate ()
            {
                Version latest = ParseVersion(Download(ChannelBase + "c3-version.txt", null));
                Version mine = ParseVersion(UpdaterVersion);
                if (latest == null || mine == null || latest <= mine) return;
                UI(delegate
                {
                    selfUpdateLink.Text = "Updater " + latest.ToString(3) + " verfügbar";
                    selfUpdateLink.LinkColor = Theme.Ok;
                    LayoutFooterLinks();
                });
            });
            t.IsBackground = true;
            t.Start();
        }

        void OnSelfUpdateClicked()
        {
            if (selfUpdateRunning) return;
            selfUpdateRunning = true;
            selfUpdateLink.Enabled = false;
            log.Clear();
            Status("Suche neuen Updater …");

            var t = new Thread(delegate ()
            {
                try { DoSelfUpdate(); }
                catch (Exception e)
                {
                    Log("FEHLER: " + e.Message);
                    Status("Updater-Update fehlgeschlagen – Details im Protokoll.");
                }
                finally
                {
                    selfUpdateRunning = false;
                    UI(delegate { selfUpdateLink.Enabled = true; });
                }
            });
            t.IsBackground = true;
            t.Start();
        }

        void DoSelfUpdate()
        {
            string channel = useDevChannel ? "Entwicklerversion" : "Stabil";
            string latestText = Download(ChannelBase + "c3-version.txt", null);
            Version latest = ParseVersion(latestText);
            Version mine = ParseVersion(UpdaterVersion);
            Log("Kanal: " + channel);
            Log("Dieser Updater: " + UpdaterVersion + " · auf GitHub: " + (latestText == null ? "–" : latestText.Trim()));

            if (latest != null && mine != null && latest < mine)
            {
                Status("Auf GitHub liegt eine ältere Version (" + latest.ToString(3) + ") – nichts zu tun.");
                return;
            }
            if (latest != null && mine != null && latest == mine)
            {
                bool again = false;
                Invoke((MethodInvoker)delegate
                {
                    again = MessageBox.Show(this,
                        "Der Updater ist aktuell (" + UpdaterVersion + ").\n\nTrotzdem neu laden? (sinnvoll bei der Entwicklerversion)",
                        "C3 Updater", MessageBoxButtons.YesNo) == DialogResult.Yes;
                });
                if (!again) { Status("Updater ist aktuell."); return; }
            }

            string newPath = ExePath + ".new";
            string oldPath = ExePath + ".old";
            Status("Lade neuen Updater …");
            Download(ChannelBase + "C3-Updater.exe", newPath);

            var info = new FileInfo(newPath);
            if (!info.Exists || info.Length < 20 * 1024 || !LooksLikeExe(newPath))
            {
                try { File.Delete(newPath); } catch { }
                throw new InvalidOperationException("Download ist keine gültige EXE.");
            }
            Log("Geladen: " + info.Length / 1024 + " KB");

            try { if (File.Exists(oldPath)) File.Delete(oldPath); } catch { }
            File.Move(ExePath, oldPath);
            try
            {
                File.Move(newPath, ExePath);
            }
            catch
            {
                File.Move(oldPath, ExePath); // Rückweg: alte Version wiederherstellen
                throw;
            }

            Log("Neuer Updater installiert – starte neu …");
            Status("Starte neu …");
            Process.Start(ExePath);
            UI(delegate { Application.Exit(); });
        }

        static bool LooksLikeExe(string path)
        {
            try
            {
                using (var f = File.OpenRead(path))
                    return f.ReadByte() == 'M' && f.ReadByte() == 'Z';
            }
            catch { return false; }
        }
    }
}
