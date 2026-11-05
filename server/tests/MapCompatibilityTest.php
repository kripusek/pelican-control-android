<?php
require __DIR__.'/../Services/MapCompatibility.php';
use Kripusek\CS2MatchControl\Services\MapCompatibility as Maps;

function check(bool $condition,string $message):void {if(!$condition)throw new RuntimeException($message);}
$installed=['de_dust2'=>'de_dust2','de_nuke'=>'de_nuke','de_inferno'=>'de_inferno','de_debris'=>'de_debris','de_mirage'=>'de_mirage'];
check(array_keys(Maps::nativeMaps($installed,'wingman'))===['de_nuke','de_inferno','de_debris'],'Wingman must filter unsupported maps');
check(Maps::nativeMaps($installed,'competitive')===$installed,'Other presets must retain all maps');
check(!Maps::workshopCompatible(['tags'=>['Competitive']],'wingman'),'Competitive tag is insufficient');
check(!Maps::workshopCompatible([],'wingman'),'Unknown compatibility is insufficient');
check(Maps::workshopCompatible(['tags'=>['Wingman']],'wingman'),'Explicit Wingman tag should work');
check(Maps::workshopCompatible(['tags'=>['2v2']],'wingman'),'Explicit 2v2 tag should work');
check(Maps::workshopCompatible([],'competitive'),'Other presets should retain workshop maps');
echo "Map compatibility: 7 checks passed\n";
