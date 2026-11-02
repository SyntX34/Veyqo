using Avalonia.Controls;

namespace Veyqo.Desktop.Views;

public partial class ErrorDialogWindow : Window
{
    public ErrorDialogWindow()
    {
        InitializeComponent();
        CloseButton.Click += (_, _) => Close();
    }

    public ErrorDialogWindow(string message) : this()
    {
        ErrorMessageText.Text = message;
    }
}

