<?php
namespace Kripusek\CS2MatchControl\Services;

final class MapCompatibility
{
    // Valve's gamemodes.txt, templates#include -> 2v2.
    public const WINGMAN_MAPS = [
        'de_debris', 'de_eldorado', 'de_poseidon', 'de_overpass',
        'de_vertigo', 'de_nuke', 'de_inferno',
    ];

    public static function nativeMaps(array $installed, string $preset): array
    {
        return $preset === 'wingman'
            ? array_intersect_key($installed, array_flip(self::WINGMAN_MAPS))
            : $installed;
    }

    public static function workshopCompatible(array $item, string $preset): bool
    {
        if ($preset !== 'wingman') return true;
        foreach ($item['tags'] ?? [] as $tag) {
            if (is_string($tag) && in_array(strtolower(trim($tag)), ['wingman', '2v2'], true)) return true;
        }
        return false;
    }
}
