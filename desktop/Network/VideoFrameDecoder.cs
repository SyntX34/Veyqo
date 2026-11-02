using System.IO;
using Avalonia.Media.Imaging;
using SkiaSharp;

namespace Veyqo.Desktop.Network;

/* High-performance decoder and renderer for incoming remote video frames */
public sealed class VideoFrameDecoder
{
    public Bitmap? DecodeFrame(byte[] frameBytes)
    {
        try
        {
            using var codec = SKCodec.Create(new SKMemoryStream(frameBytes));
            if (codec != null)
            {
                var imageInfo = new SKImageInfo(codec.Info.Width, codec.Info.Height, SKColorType.Bgra8888, SKAlphaType.Premul);
                using var skBitmap = new SKBitmap(imageInfo);
                var result = codec.GetPixels(imageInfo, skBitmap.GetPixels());
                if (result == SKCodecResult.Success)
                {
                    using var image = SKImage.FromBitmap(skBitmap);
                    using var data = image.Encode(SKEncodedImageFormat.Jpeg, 85);
                    using var stream = data.AsStream();
                    return new Bitmap(stream);
                }
            }

            /* Fallback direct stream attempt */
            using var fallbackStream = new MemoryStream(frameBytes);
            return new Bitmap(fallbackStream);
        }
        catch
        {
            return null;
        }
    }
}

