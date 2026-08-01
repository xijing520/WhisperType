# Generate VoskType application icon (.ico) using inline C#
$ErrorActionPreference = "Stop"

$iconPath = Join-Path $PSScriptRoot "app-icon.ico"
if (Test-Path $iconPath) { Remove-Item $iconPath -Force }

$csharp = @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

public class IconGenerator
{
    public static void Generate(string path)
    {
        int size = 256;
        using (Bitmap bmp = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        using (Graphics g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Color.Transparent);

            Rectangle bgRect = new Rectangle(12, 12, 232, 232);
            using (LinearGradientBrush brush = new LinearGradientBrush(
                bgRect,
                Color.FromArgb(70, 200, 100),
                Color.FromArgb(30, 140, 60),
                LinearGradientMode.ForwardDiagonal))
            {
                g.FillEllipse(brush, bgRect);
            }

            Rectangle micRect = new Rectangle(106, 64, 44, 88);
            GraphicsPath micPath = RoundedRect(micRect, 22);
            SolidBrush whiteBrush = new SolidBrush(Color.White);
            g.FillPath(whiteBrush, micPath);
            micPath.Dispose();
            whiteBrush.Dispose();

            Pen pen = new Pen(Color.White, 9);
            pen.StartCap = LineCap.Round;
            pen.EndCap = LineCap.Round;
            g.DrawArc(pen, 84, 84, 88, 118, 200, 140);
            g.DrawLine(pen, 98, 200, 158, 200);
            g.DrawLine(pen, 128, 200, 128, 178);
            pen.Dispose();

            SaveAsIco(bmp, path);
        }
    }

    static GraphicsPath RoundedRect(Rectangle r, int radius)
    {
        GraphicsPath path = new GraphicsPath();
        path.AddArc(r.X, r.Y, radius, radius, 180, 90);
        path.AddArc(r.Right - radius, r.Y, radius, radius, 270, 90);
        path.AddArc(r.Right - radius, r.Bottom - radius, radius, radius, 0, 90);
        path.AddArc(r.X, r.Bottom - radius, radius, radius, 90, 90);
        path.CloseFigure();
        return path;
    }

    static void SaveAsIco(Bitmap source, string path)
    {
        int[] sizes = { 256, 128, 64, 48, 32, 16 };
        using (FileStream fs = new FileStream(path, FileMode.Create))
        using (BinaryWriter writer = new BinaryWriter(fs))
        {
            writer.Write((short)0);
            writer.Write((short)1);
            writer.Write((short)sizes.Length);

            int dataOffset = 6 + 16 * sizes.Length;
            var images = new System.Collections.Generic.List<byte[]>();

            foreach (int sz in sizes)
            {
                using (Bitmap resized = new Bitmap(source, sz, sz))
                {
                    byte[] pngData;
                    using (MemoryStream ms = new MemoryStream())
                    {
                        resized.Save(ms, ImageFormat.Png);
                        pngData = ms.ToArray();
                    }
                    images.Add(pngData);

                    writer.Write((byte)(sz >= 256 ? 0 : sz));
                    writer.Write((byte)(sz >= 256 ? 0 : sz));
                    writer.Write((byte)0);
                    writer.Write((byte)0);
                    writer.Write((short)1);
                    writer.Write((short)32);
                    writer.Write((int)pngData.Length);
                    writer.Write((int)dataOffset);
                    dataOffset += pngData.Length;
                }
            }

            foreach (byte[] img in images)
            {
                writer.Write(img);
            }
        }
    }
}
"@

Add-Type -TypeDefinition $csharp -ReferencedAssemblies System.Drawing
[IconGenerator]::Generate($iconPath)

Write-Host "Icon generated: $iconPath"
