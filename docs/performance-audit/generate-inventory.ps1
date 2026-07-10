param(
    [string]$ModsPath = 'F:\CurseForge\Instances\NJC2 (RPG Series expansion)\mods',
    [string]$OutputPath = (Join-Path $PSScriptRoot 'MOD-INVENTORY.md')
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Read-ZipText($zip, [string]$name) {
    $entry = $zip.Entries | Where-Object FullName -eq $name | Select-Object -First 1
    if (-not $entry) { return $null }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Toml-Value([string]$text, [string]$key) {
    $match = [regex]::Match($text, '(?m)^\s*' + [regex]::Escape($key) + '\s*=\s*"([^"]+)"')
    if ($match.Success) { return $match.Groups[1].Value }
    return $null
}

function Classify([string]$file, [string]$id, [string[]]$entries) {
    $s = ($file + ' ' + $id).ToLowerInvariant()
    $client = 'appleskin|borderless|cherishedworlds|controlling|distanthorizons|emi|entity_model|entity_texture|entityculling|freecam|gpumem|immediatelyfast|iris|jade|jei|mousetweaks|ok_zoomer|reeses|skinlayers|sodium|sound.physics|xaero|camera|dragnsounds'
    if ($s -match $client) { return @('Client-only', 'No', 'High') }
    if ($s -match 'architectury|balm|blockui|cloth.config|collective|cupboard|curios|epherolib|framework|fzzy|geckolib|kotlinforforge|moonlight|multipiston|owo.lib|patchouli|platform|puzzleslib|resourcefullib|searchables|sophisticatedcore|structurize|txnilib|yungsapi|forgified.fabric.api') {
        return @('Library/dependency (environment follows dependants)', 'Usually yes', 'Medium')
    }
    return @('Shared client and server', 'Yes', 'Medium')
}

function Category([string]$file, [string]$id) {
    $s = ($file + ' ' + $id).ToLowerInvariant()
    if ($s -match 'minecolon') { return 'Colony AI/pathfinding/logistics' }
    if ($s -match 'create|flywheel|copycat|sliceanddice') { return 'Create machinery/add-on' }
    if ($s -match 'yung|structure|repurposed|gazebo|fireflybush') { return 'World generation/structures' }
    if ($s -match 'sodium|iris|entity.*feature|entityculling|immediatelyfast|distanthorizon|skinlayer|sound.physics|gpumem') { return 'Client rendering/memory' }
    if ($s -match 'modernfix|ferrite|ai.improvement|smoothchunk|chunk.?sending|connectivity|packetfixer|clumps|optimization') { return 'Optimization/network' }
    if ($s -match 'naturalist|illager|friend|guardvillager|goblin|ribbit|skeleton|pillag') { return 'Entity AI/spawning' }
    if ($s -match 'map|voicechat|watut') { return 'Networking/client state' }
    if ($s -match 'architectury|api|lib|framework|kotlin|cloth|balm|curios|accessories') { return 'Library/API' }
    return 'Gameplay/content/UI' 
}

$rows = foreach ($jar in Get-ChildItem -LiteralPath $ModsPath -File | Sort-Object Name) {
    $enabled = $jar.Extension -eq '.jar'
    $id = $null; $name = $null; $version = $null; $loader = 'Unknown'; $deps = @(); $embedded = 0; $evidence = 'Filename only'
    try {
        $zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
        try {
            $entries = @($zip.Entries.FullName)
            $metaName = @('META-INF/neoforge.mods.toml','META-INF/mods.toml') | Where-Object { $entries -contains $_ } | Select-Object -First 1
            if ($metaName) {
                $loader = if ($metaName -like '*neoforge*') { 'NeoForge' } else { 'Forge/NeoForge' }
                $meta = Read-ZipText $zip $metaName
                $id = Toml-Value $meta 'modId'; $name = Toml-Value $meta 'displayName'; $version = Toml-Value $meta 'version'
                $deps = @([regex]::Matches($meta, '(?m)^\s*modId\s*=\s*["'']([^"'']+)["'']') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne $id } | Select-Object -Unique)
                $evidence = $metaName
            } elseif ($entries -contains 'fabric.mod.json') {
                $loader = 'Fabric metadata'; $fm = (Read-ZipText $zip 'fabric.mod.json' | ConvertFrom-Json)
                $id=$fm.id; $name=$fm.name; $version=$fm.version; $deps=@($fm.depends.PSObject.Properties.Name); $evidence='fabric.mod.json'
            }
            $embedded = @($entries | Where-Object { $_ -match '^META-INF/jarjar/.+\.jar$' }).Count
        } finally { $zip.Dispose() }
    } catch { $evidence = 'Unreadable as ZIP: ' + $_.Exception.Message }
    if (-not $id -and $jar.Name -like 'kotlinforforge-*') { $id='kotlinforforge'; $name='Kotlin for Forge'; $version=($jar.BaseName -replace '^kotlinforforge-','' -replace '-all$',''); $loader='NeoForge library wrapper'; $evidence='filename plus embedded jar-in-jar metadata' }
    if (-not $id) { $id = '(unresolved)' }
    if (-not $name) { $name = '(metadata unresolved)' }
    if (-not $version -or $version -match '^\$\{.+\}$') { $version = 'filename-inferred: ' + $jar.BaseName; $evidence += '; metadata version placeholder' }
    $class = Classify $jar.Name $id @()
    [pscustomobject]@{ File=$jar.Name; Enabled=$enabled; Name=$name; Id=$id; Version=$version; Loader=$loader; Environment=$class[0]; Server=$class[1]; Confidence=$class[2]; Category=(Category $jar.Name $id); Dependencies=($deps -join ', '); Embedded=$embedded; Evidence=$evidence }
}

$lines = [Collections.Generic.List[string]]::new()
$lines.Add('# Mod Inventory')
$lines.Add('')
$lines.Add('Generated by read-only inspection of the authoritative client `mods` directory. Environment is conservative: metadata rarely declares physical side, so name-based classifications are explicitly not equivalent to a dedicated-server launch test. `.jar.disabled` files are inventoried but inactive.')
$lines.Add('')
$lines.Add("- Audit date: $(Get-Date -Format yyyy-MM-dd)")
$lines.Add("- Artifacts: $($rows.Count) total; $(@($rows | Where-Object Enabled).Count) enabled; $(@($rows | Where-Object { -not $_.Enabled }).Count) disabled")
$lines.Add('- Loader baseline: Minecraft 1.21.1 / NeoForge 21.1.226 (request baseline)')
$lines.Add('')
$lines.Add('| Exact filename | Mod name | Mod ID | Version | Loader | Environment | Dedicated server | Performance category | Dependencies found | Jar-in-jar | Confidence/evidence |')
$lines.Add('|---|---|---|---|---|---|---|---|---|---:|---|')
foreach ($r in $rows) {
    $vals = @($r.File,$r.Name,$r.Id,$r.Version,$r.Loader,$r.Environment,$r.Server,$r.Category,($(if($r.Dependencies){$r.Dependencies}else{'None declared/found'})),$r.Embedded,($r.Confidence + '; ' + $r.Evidence)) | ForEach-Object { ("$_").Replace('|','\|').Replace("`r",' ').Replace("`n",' ') }
    $lines.Add('| ' + ($vals -join ' | ') + ' |')
}
$lines.Add('')
$lines.Add('## Inventory Flags')
$lines.Add('')
$lines.Add('- Disabled artifacts are not active: ArmorPoser, Character Selector, Chunk Sending, and Packet Fixer filenames end in `.jar.disabled`.')
$lines.Add('- `forgified-fabric-api` is an intentional NeoForge compatibility layer; Fabric metadata alone is not proof that an artifact is incorrectly installed.')
$lines.Add('- No duplicate exact filenames were observed. Mod-ID duplication requires review of the table because a JAR may expose several mod IDs.')
$lines.Add('- Jar-in-jar counts identify embedded dependencies; they are not separate top-level installed mods.')
$lines.Add('- Every `Medium` environment result requires verification against metadata/classes before constructing a production server mod directory.')

[IO.File]::WriteAllLines($OutputPath, $lines, [Text.UTF8Encoding]::new($false))
