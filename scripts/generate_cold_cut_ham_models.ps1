param(
    [string]$ReferenceRoot = (Join-Path $PSScriptRoot '..\..\xuexi\KaleidoscopeCookery-main2'),
    [string]$PackRoot = (Join-Path $PSScriptRoot '..\Kaleidoscope\kaleidoscopecookery\resourcepack')
)

$sourceModel = Join-Path $ReferenceRoot 'src\main\resources\assets\kaleidoscope_cookery\models\item\cold_cut_ham_slices_block.json'
$sourceTexture = Join-Path $ReferenceRoot 'src\main\resources\assets\kaleidoscope_cookery\textures\block\cold_cut_ham_slices.png'
$modelDir = Join-Path $PackRoot 'assets\minecraft\models\block\custom\cook\block\food'
$textureDir = Join-Path $PackRoot 'assets\minecraft\textures\block\custom\cook\block\food'

if (-not (Test-Path -LiteralPath $sourceModel) -or -not (Test-Path -LiteralPath $sourceTexture)) {
    throw 'Cold-cut ham reference model or texture is missing.'
}

New-Item -ItemType Directory -Force -Path $modelDir | Out-Null
New-Item -ItemType Directory -Force -Path $textureDir | Out-Null
Copy-Item -LiteralPath $sourceTexture -Destination (Join-Path $textureDir 'cold_cut_ham_slices.png') -Force

$rawModel = Get-Content -LiteralPath $sourceModel -Raw
for ($stage = 0; $stage -le 8; $stage++) {
    $model = $rawModel | ConvertFrom-Json
    $model.textures.'0' = 'minecraft:block/custom/cook/block/food/cold_cut_ham_slices'
    $model | Add-Member -NotePropertyName 'render_type' -NotePropertyValue 'minecraft:cutout' -Force
    $remaining = 8 - $stage
    $model.elements = @($model.elements | Where-Object {
        if ($_.name -eq 'base') {
            return $true
        }
        if ($_.name -match '^bite([1-8])$') {
            return [int]$Matches[1] -le $remaining
        }
        return $false
    })
    $model.PSObject.Properties.Remove('groups')
    $target = Join-Path $modelDir ("cold_cut_ham_slices_{0}.json" -f $stage)
    $model | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $target -Encoding UTF8
}

Write-Output 'Generated cold-cut ham stages 0-8 and copied its reference texture.'
