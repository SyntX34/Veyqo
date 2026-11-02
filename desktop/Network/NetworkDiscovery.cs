using System.Net;
using System.Net.Sockets;

namespace Veyqo.Desktop.Network;

/* Discovers local network interfaces and provides QR metadata payload */
public static class NetworkDiscovery
{
    public static string GetLocalIpAddress()
    {
        try
        {
            using var socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, 0);
            socket.Connect("8.8.8.8", 65530);
            if (socket.LocalEndPoint is IPEndPoint endPoint)
            {
                return endPoint.Address.ToString();
            }
        }
        catch
        {
            /* Fallback to interface scan */
            var host = Dns.GetHostEntry(Dns.GetHostName());
            foreach (var ip in host.AddressList)
            {
                if (ip.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ip))
                {
                    return ip.ToString();
                }
            }
        }

        return "127.0.0.1";
    }

    public static string BuildConnectionPayload(string host, int port, string secretToken)
    {
        return $"veyqo://connect?host={host}&port={port}&token={secretToken}&v=1";
    }
}
