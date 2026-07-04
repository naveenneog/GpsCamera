param(
  [switch]$Force
)
# Generates the GPS Camera app logo with Azure AI Foundry gpt-image-2 (AAD auth),
# then produces adaptive-icon foregrounds + legacy launcher PNGs for every density.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = "C:\Users\navg\DailyApps\GpsCamera"
$res = "$root\app\src\main\res"
$docImg = "$root\docs\img"
New-Item -ItemType Directory -Force -Path $docImg | Out-Null
$logoPath = "$docImg\logo.png"

$endpoint = "https://ai-contosohub530569751908.cognitiveservices.azure.com"
$uri = "$endpoint/openai/deployments/gpt-image-2/images/generations?api-version=2025-04-01-preview"

function Log($m) { Write-Host "$(Get-Date -Format HH:mm:ss) $m" }

if ((Test-Path $logoPath) -and -not $Force) {
  Log "logo.png already exists; use -Force to regenerate. Reslicing icons only."
} else {
  $token = az account get-access-token --resource https://cognitiveservices.azure.com --query accessToken -o tsv
  $prompt = "A modern flat vector app icon for a GPS Camera mobile app. A minimalist camera fused with a location map pin, teal (#00BFA5) and warm amber (#FFB300) accents on a dark charcoal (#0E1116) rounded-square background. Centered, bold, high contrast, clean geometric shapes, subtle depth, no text, no words, no letters, professional launcher icon style."
  $body = @{ model = "gpt-image-2"; prompt = $prompt; n = 1; size = "1024x1024" } | ConvertTo-Json
  $headers = @{ "Authorization" = "Bearer $token"; "Content-Type" = "application/json" }

  $attempt = 0; $ok = $false
  while (-not $ok -and $attempt -lt 8) {
    $attempt++
    try {
      Log "Requesting logo (attempt $attempt)..."
      $resp = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -Body $body -TimeoutSec 180
      $b64 = $resp.data[0].b64_json
      [IO.File]::WriteAllBytes($logoPath, [Convert]::FromBase64String($b64))
      Log "Saved $logoPath"
      $ok = $true
    } catch {
      $emsg = "$($_.Exception.Message)"
      if ($emsg -match "429|Too Many") { $w = [Math]::Min(20 + $attempt * 10, 75); Log "429 -> wait ${w}s"; Start-Sleep $w }
      elseif ($emsg -match "401|expired") { $token = az account get-access-token --resource https://cognitiveservices.azure.com --query accessToken -o tsv; $headers["Authorization"] = "Bearer $token" }
      else { Log "ERR: $emsg"; Start-Sleep 5 }
    }
  }
  if (-not $ok) { throw "Logo generation failed after $attempt attempts" }
}

# ---- Slice icons from the master logo ----
$src = [System.Drawing.Image]::FromFile($logoPath)

function Save-Png($bmp, $path) {
  New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function New-Canvas([int]$size) {
  $bmp = New-Object System.Drawing.Bitmap $size, $size
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  return @($bmp, $g)
}

# Legacy full-bleed launcher (square) sizes
$legacy = @{ "mdpi" = 48; "hdpi" = 72; "xhdpi" = 96; "xxhdpi" = 144; "xxxhdpi" = 192 }
# Adaptive foreground sizes (108dp bucket) — subject scaled into the safe zone
$fg = @{ "mdpi" = 108; "hdpi" = 162; "xhdpi" = 216; "xxhdpi" = 324; "xxxhdpi" = 432 }

foreach ($d in $legacy.Keys) {
  $s = $legacy[$d]
  $c = New-Canvas $s; $bmp = $c[0]; $g = $c[1]
  $g.DrawImage($src, 0, 0, $s, $s)
  Save-Png $bmp "$res\mipmap-$d\ic_launcher.png"
  # round variant: circular clip
  $c2 = New-Canvas $s; $bmp2 = $c2[0]; $g2 = $c2[1]
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $path.AddEllipse(0, 0, $s, $s)
  $g2.SetClip($path)
  $g2.DrawImage($src, 0, 0, $s, $s)
  Save-Png $bmp2 "$res\mipmap-$d\ic_launcher_round.png"
  $g.Dispose(); $bmp.Dispose(); $g2.Dispose(); $bmp2.Dispose()
}

foreach ($d in $fg.Keys) {
  $s = $fg[$d]
  $c = New-Canvas $s; $bmp = $c[0]; $g = $c[1]
  # draw the logo at 68% centered (transparent margin => adaptive safe zone)
  $inner = [int]($s * 0.68)
  $off = [int](($s - $inner) / 2)
  $g.DrawImage($src, $off, $off, $inner, $inner)
  Save-Png $bmp "$res\mipmap-$d\ic_launcher_foreground.png"
  $g.Dispose(); $bmp.Dispose()
}

$src.Dispose()
Log "Icon slicing complete."
