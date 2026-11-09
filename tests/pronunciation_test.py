from copy import deepcopy
from pathlib import Path
from types import SimpleNamespace as Object
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parent.parent/'lib'))
from pronunciation import natural_pronunciation

def mora(text,vowel,length,pitch,consonant=None,consonant_length=None):
    return Object(text=text,vowel=vowel,vowel_length=length,pitch=pitch,consonant=consonant,consonant_length=consonant_length)

class PronunciationTest(unittest.TestCase):
    def test_iie_keeps_both_i_moras_and_lexical_accent(self):
        phrase=Object(accent=3,moras=[mora('イ','i',.169,5.692),mora('イ','i',.112,5.838),mora('エ','e',.192,5.939)])
        query=Object(accent_phrases=[phrase]);before=deepcopy(query)
        natural_pronunciation(query)
        self.assertEqual([m.text for m in phrase.moras],['イ','イ','エ'])
        self.assertEqual([m.pitch for m in phrase.moras],[m.pitch for m in before.accent_phrases[0].moras])
        self.assertEqual(phrase.accent,3)
        self.assertLess(sum(m.vowel_length for m in phrase.moras[:2]),.281)
        self.assertEqual(phrase.moras[2].vowel_length,.192)

    def test_all_long_vowels_keep_phonemes_and_two_moras(self):
        for vowel in 'aiueo':
            phrase=Object(accent=1,moras=[mora(vowel,vowel,.17,5.7),mora(vowel,vowel,.17,5.6)])
            natural_pronunciation(Object(accent_phrases=[phrase]))
            self.assertEqual([m.vowel for m in phrase.moras],[vowel,vowel])
            self.assertEqual([m.pitch for m in phrase.moras],[5.7,5.6])
            self.assertTrue(all(m.vowel_length>0 for m in phrase.moras))

    def test_konnichiwa_nasal_and_n_are_never_merged(self):
        phrase=Object(accent=2,moras=[mora('ン','N',.3,5.8),mora('ニ','i',.09,5.9,'n',.15)])
        natural_pronunciation(Object(accent_phrases=[phrase]))
        self.assertEqual([m.text for m in phrase.moras],['ン','ニ'])
        self.assertEqual(phrase.moras[0].vowel,'N')
        self.assertEqual(phrase.moras[1].consonant,'n')
        self.assertLess(phrase.moras[0].vowel_length+phrase.moras[1].consonant_length,.45)

    def test_normal_durations_and_phrase_boundaries_are_preserved(self):
        query=Object(accent_phrases=[Object(accent=1,moras=[mora('オ','o',.116,5.6),mora('オ','o',.116,5.8)]),Object(accent=1,moras=[mora('オ','o',.17,5.7)])])
        before=deepcopy(query)
        natural_pronunciation(query)
        self.assertEqual(query,before)

if __name__=='__main__':unittest.main()
