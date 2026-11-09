$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$voice = $null
$voices = @{}
$initializationError = $null
try {
    $settings = Get-ItemProperty 'HKCU:\Software\NaturalVoiceSAPIAdapter\Enumerator'
    if ($settings.NoEdgeVoices -ne 1 -or $settings.NoAzureVoices -ne 1) { throw 'Adapter must have online voices disabled.' }
    $voice = New-Object -ComObject SAPI.SpVoice
    $category = New-Object -ComObject SAPI.SpObjectTokenCategory
    $category.SetId('HKEY_CURRENT_USER\Software\Microsoft\Speech\Voices', $false)
    $tokens = $category.EnumerateTokens('', '')
    for ($i=0; $i -lt $tokens.Count; $i++) {
        $token = $tokens.Item($i)
        if ($token.GetAttribute('NaturalVoiceType') -notmatch '(^|;)Local(;|$)' -or $token.GetAttribute('Locale') -ne 'ja-JP') { continue }
        $name = $token.GetAttribute('Name')
        if ($name -eq 'Microsoft Keita') { $voices['ja-JP-KeitaNeural'] = $token }
        if ($name -eq 'Microsoft Nanami') { $voices['ja-JP-NanamiNeural'] = $token }
    }
    if ($voices.Count -ne 2) { throw 'Install both Microsoft Keita and Nanami Narrator natural voices.' }
} catch { $initializationError = $_.Exception.Message }

while ($null -ne ($line = [Console]::ReadLine())) {
    $request = $null
    try {
        $request = ConvertFrom-Json $line
        if ($initializationError) { throw $initializationError }
        if ($request.op -eq 'voices') {
            $items = @($voices.Keys | Sort-Object | ForEach-Object {
                $token = $voices[$_]
                @{ShortName=$_; FriendlyName=$token.GetDescription(); Gender=$token.GetAttribute('Gender'); Locale='ja-JP'; Offline=$true}
            })
            $result = @{id=$request.id; voices=$items}
        } elseif ($request.op -eq 'synthesize') {
            if (-not $voices.ContainsKey([string]$request.voice)) { throw 'Unknown offline voice.' }
            $speed = [double]$request.speed
            if ($speed -lt 0.7 -or $speed -gt 2) { throw 'Invalid speech speed.' }
            if (-not $request.text -or $request.text.Length -gt 4000) { throw 'Invalid speech text.' }
            $voice.Voice = $voices[[string]$request.voice]
            $voice.Rate = 0
            $stream = New-Object -ComObject SAPI.SpMemoryStream
            $stream.Format.Type = 26 # 24 kHz, 16-bit, mono PCM
            try {
                $voice.AudioOutputStream = $stream
                $rate = (($speed - 1) * 100).ToString('0.####', [Globalization.CultureInfo]::InvariantCulture) + '%'
                $text = [Security.SecurityElement]::Escape([string]$request.text)
                # The adapter passes this SSML prosody tag through without rounding to SAPI's integer rate steps.
                [void]$voice.Speak(('<prosody rate="' + $rate + '">' + $text + '</prosody>'), 8)
                $bytes = [byte[]]$stream.GetData()
                $result = @{id=$request.id; audio=[Convert]::ToBase64String($bytes)}
            } finally {
                [void][Runtime.InteropServices.Marshal]::ReleaseComObject($stream)
            }
        } else { throw 'Unknown offline operation.' }
    } catch { $result = @{id=$request.id; error=$_.Exception.Message} }
    [Console]::WriteLine((ConvertTo-Json -InputObject $result -Depth 5 -Compress))
}
