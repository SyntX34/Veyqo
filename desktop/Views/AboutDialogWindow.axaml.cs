using System.Diagnostics;
using Avalonia.Controls;

namespace Veyqo.Desktop.Views;

public partial class AboutDialogWindow : Window
{
    public AboutDialogWindow()
    {
        InitializeComponent();

        VersionDetailText.Text = $"Version: v{VersionInfo.Version} (Official Build)";

        OkButton.Click += (_, _) => Close();

        GithubLinkButton.Click += (_, _) =>
        {
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = "https://github.com/SyntX34/Veyqo",
                    UseShellExecute = true
                });
            }
            catch
            {
            }
        };
    }
}

