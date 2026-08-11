package com.tengames.fixtures;

/**
 * @param tla the three-letter abbreviation (PSG, FCB, MUN). Kept because it is
 *            what someone types on a phone, and it appears nowhere in the
 *            club's actual name.
 */
public record ProviderTeam(String providerRef, String name, String shortName, String crestUrl, String tla) {
}
