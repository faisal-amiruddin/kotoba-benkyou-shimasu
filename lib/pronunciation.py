"""Conservative VOICEVOX duration correction; never delete Japanese morae."""

def natural_pronunciation(query):
    for phrase in query.accent_phrases:
        moras = phrase.moras
        start = 0
        while start < len(moras):
            end = start + 1
            vowel = moras[start].vowel
            if vowel in ('a', 'i', 'u', 'e', 'o'):
                while end < len(moras) and moras[end].vowel == vowel and moras[end].consonant is None:
                    end += 1
                if end - start >= 2:
                    total = sum(m.vowel_length for m in moras[start:end])
                    limit = 0.12 * (end - start)
                    if total > limit:
                        for mora in moras[start:end]:
                            mora.vowel_length *= limit / total
            # Keep the nasal N and following n separate, but limit abnormal holds.
            if vowel == 'N':
                moras[start].vowel_length = min(moras[start].vowel_length, 0.18)
                if start + 1 < len(moras) and moras[start + 1].consonant == 'n':
                    following = moras[start + 1]
                    length = following.consonant_length or 0
                    total = moras[start].vowel_length + length
                    if total > 0.20:
                        moras[start].vowel_length *= 0.20 / total
                        following.consonant_length = length * 0.20 / total
            start = end
    return query
