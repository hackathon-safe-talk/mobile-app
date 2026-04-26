Add-Type -AssemblyName System.Drawing

function Crop-TransparentEdges {
    param([string]$imagePath, [string]$newImagePath)
    
    try {
        $bmp = [System.Drawing.Bitmap]::FromFile($imagePath)
        $minX = $bmp.Width
        $minY = $bmp.Height
        $maxX = 0
        $maxY = 0

        for ($y = 0; $y -lt $bmp.Height; $y++) {
            for ($x = 0; $x -lt $bmp.Width; $x++) {
                $pixel = $bmp.GetPixel($x, $y)
                if ($pixel.A -gt 15) { 
                    if ($x -lt $minX) { $minX = $x }
                    if ($x -gt $maxX) { $maxX = $x }
                    if ($y -lt $minY) { $minY = $y }
                    if ($y -gt $maxY) { $maxY = $y }
                }
            }
        }

        if ($minX -gt $maxX) {
            Write-Host "Empty image: $imagePath"
            $bmp.Dispose()
            return
        }

        $width = $maxX - $minX + 1
        $height = $maxY - $minY + 1
        
        $pad = [Math]::Floor($width * 0.05)
        $newWidth = $width + ($pad * 2)
        $newHeight = $height + ($pad * 2)
        $maxDim = [Math]::Max($newWidth, $newHeight)

        $croppedBmp = New-Object System.Drawing.Bitmap($maxDim, $maxDim)
        $g = [System.Drawing.Graphics]::FromImage($croppedBmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        
        $destX = ($maxDim - $width) / 2
        $destY = ($maxDim - $height) / 2
        
        $sourceRect = New-Object System.Drawing.Rectangle($minX, $minY, $width, $height)
        $g.DrawImage($bmp, $destX, $destY, $sourceRect, [System.Drawing.GraphicsUnit]::Pixel)

        $g.Dispose()
        $bmp.Dispose()
        
        $croppedBmp.Save($newImagePath, [System.Drawing.Imaging.ImageFormat]::Png)
        $croppedBmp.Dispose()
        Write-Host "Success: $newImagePath"
    } catch {
        Write-Host "Error: $($_.Exception.Message)"
    }
}

 = "d:\SafeTalk\app\src\main\res\drawable"
Crop-TransparentEdges "\safetalk_shield.png" "\safetalk_shield_cropped.png"
Crop-TransparentEdges "\shield_safe.png" "\shield_safe_cropped.png"
Crop-TransparentEdges "\shield_warning.png" "\shield_warning_cropped.png"
Crop-TransparentEdges "\shield_danger.png" "\shield_danger_cropped.png"
