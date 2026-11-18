Add-Type -AssemblyName System.Drawing
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$output = Join-Path $root 'app/src/main/resources/dev/recipeeditor/app/icons'
[System.IO.Directory]::CreateDirectory($output) | Out-Null
$images = [System.Collections.Generic.List[byte[]]]::new()
$sizes = @(16,32,48,64,128,256)
foreach ($size in $sizes) {
    $bitmap = [System.Drawing.Bitmap]::new($size,$size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $background = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255,32,36,46))
    $accent = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255,111,181,239))
    $outputBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255,215,228,244))
    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.FillRectangle($background,0,0,$size,$size)
        for ($row=0; $row -lt 3; $row++) {
            for ($col=0; $col -lt 3; $col++) {
                $graphics.FillRectangle($accent,[single]($size*(0.13+0.17*$col)),[single]($size*(0.2+0.21*$row)),[single]($size*0.12),[single]($size*0.15))
            }
        }
        $graphics.FillRectangle($outputBrush,[single]($size*0.74),[single]($size*0.4),[single]($size*0.15),[single]($size*0.2))
        $stream = [System.IO.MemoryStream]::new()
        try {
            $bitmap.Save($stream,[System.Drawing.Imaging.ImageFormat]::Png)
            $bytes = $stream.ToArray()
            $images.Add($bytes)
            [System.IO.File]::WriteAllBytes((Join-Path $output "app-$size.png"),$bytes)
        } finally { $stream.Dispose() }
    } finally { $graphics.Dispose(); $bitmap.Dispose(); $background.Dispose(); $accent.Dispose(); $outputBrush.Dispose() }
}
$ico = [System.IO.BinaryWriter]::new([System.IO.File]::Create((Join-Path $root 'packaging/icons/MinecraftRecipeEditor.ico')))
try {
    $ico.Write([uint16]0); $ico.Write([uint16]1); $ico.Write([uint16]$sizes.Count)
    $offset = 6 + 16*$sizes.Count
    for ($index=0; $index -lt $sizes.Count; $index++) {
        $dimension = if ($sizes[$index] -eq 256) {0} else {$sizes[$index]}
        $ico.Write([byte]$dimension); $ico.Write([byte]$dimension); $ico.Write([byte]0); $ico.Write([byte]0)
        $ico.Write([uint16]1); $ico.Write([uint16]32); $ico.Write([uint32]$images[$index].Length); $ico.Write([uint32]$offset)
        $offset += $images[$index].Length
    }
    foreach ($bytes in $images) { $ico.Write($bytes) }
} finally { $ico.Dispose() }
