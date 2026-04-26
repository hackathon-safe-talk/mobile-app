"""Check overlaps between dataset versions to understand superset relationships."""
import pandas as pd

# Master semantic datasets
v7 = pd.read_csv('data/processed/master_semantic_dataset_v7.csv', encoding='utf-8', on_bad_lines='skip')
v8 = pd.read_csv('data/processed/master_semantic_dataset_v8.csv', encoding='utf-8', on_bad_lines='skip')
v9 = pd.read_csv('data/processed/master_semantic_dataset_v9_cleaned.csv', encoding='utf-8', on_bad_lines='skip')

print(f"master_semantic v7: {len(v7)}, v8: {len(v8)}, v9: {len(v9)}")

v9_in_v7 = v9['text'].isin(v7['text']).sum()
v8_in_v7 = v8['text'].isin(v7['text']).sum()
v7_in_v8 = v7['text'].isin(v8['text']).sum()
v7_in_v9 = v7['text'].isin(v9['text']).sum()

print(f"v9 texts in v7: {v9_in_v7}/{len(v9)}")
print(f"v8 texts in v7: {v8_in_v7}/{len(v8)}")
print(f"v7 texts in v8: {v7_in_v8}/{len(v7)}")
print(f"v7 texts in v9: {v7_in_v9}/{len(v7)}")

# Check cleaned vs non-cleaned
v7c = pd.read_csv('data/processed/master_semantic_dataset_v7_cleaned.csv', encoding='utf-8', on_bad_lines='skip')
v8c = pd.read_csv('data/processed/master_semantic_dataset_v8_cleaned.csv', encoding='utf-8', on_bad_lines='skip')
print(f"\nv7 cleaned same texts as v7: {v7['text'].equals(v7c['text'])}")
print(f"v8 cleaned same texts as v8: {v8['text'].equals(v8c['text'])}")

# Are they IDENTICALLY same rows?
v7_diff = (v7['text'] != v7c['text']).sum()
v8_diff = (v8['text'] != v8c['text']).sum()
print(f"v7 vs v7_cleaned text diffs: {v7_diff}")
print(f"v8 vs v8_cleaned text diffs: {v8_diff}")

# Overlap messages_cleaned_v6 <-> master_semantic_v7
mc6 = pd.read_csv('data/processed/messages_cleaned_v6.csv', encoding='utf-8', on_bad_lines='skip')
mc6_in_v7 = mc6['text'].isin(v7['text']).sum()
print(f"\nmc_v6 texts in master_v7: {mc6_in_v7}/{len(mc6)}")

# Overlap unified_v6 <-> master_v7
u6 = pd.read_csv('data/final/safetalk_unified_dataset_v6.csv', encoding='utf-8', on_bad_lines='skip')
v7_in_u6 = v7['text'].isin(u6['text']).sum()
u6_in_v7 = u6['text'].isin(v7['text']).sum()
print(f"master_v7 texts in unified_v6: {v7_in_u6}/{len(v7)}")
print(f"unified_v6 texts in master_v7: {u6_in_v7}/{len(u6)}")

# Check consolidated datasets overlap with others
en_v2 = pd.read_csv('data/consolidated/en_final_v2.csv', encoding='utf-8', on_bad_lines='skip')
eng1 = pd.read_csv('data/consolidated/eng_final1_dataset.csv', encoding='utf-8', on_bad_lines='skip')
print(f"\nen_final_v2: {len(en_v2)}, eng_final1: {len(eng1)}")
eng1_in_env2 = eng1['text'].isin(en_v2['text']).sum()
print(f"eng_final1 in en_final_v2: {eng1_in_env2}/{len(eng1)}")

# Check uzbek policy seeds overlap
ups = pd.read_csv('data/uzbek_policy_seeds.csv', encoding='utf-8', on_bad_lines='skip')
ups_in_v7 = ups['text'].isin(v7['text']).sum()
print(f"\nuzb_policy_seeds: {len(ups)}, in master_v7: {ups_in_v7}")

# Check expansions overlap
hard = pd.read_csv('data/expansions/hard_cases_v8.csv', encoding='utf-8', on_bad_lines='skip')
harden = pd.read_csv('data/expansions/hardening_data_v9.csv', encoding='utf-8', on_bad_lines='skip')
realistic = pd.read_csv('data/expansions/realistic_v6_messages.csv', encoding='utf-8', on_bad_lines='skip')
hard_in_v7 = hard['text'].isin(v7['text']).sum()
harden_in_v7 = harden['text'].isin(v7['text']).sum()
real_in_v7 = realistic['text'].isin(v7['text']).sum()
print(f"\nExpansion overlaps with master_v7:")
print(f"  hard_cases_v8 in v7: {hard_in_v7}/{len(hard)}")
print(f"  hardening_data_v9 in v7: {harden_in_v7}/{len(harden)}")
print(f"  realistic_v6 in v7: {real_in_v7}/{len(realistic)}")

# Check source datasets
sms_txt = pd.read_csv('data/sources/sms_spam_collection.txt', sep='\t', header=None, encoding='utf-8')
sms_txt.columns = ['label', 'text']
spam_csv = pd.read_csv('data/sources/spam.csv', encoding='utf-8', on_bad_lines='skip')
sms_in_spam = sms_txt['text'].isin(spam_csv['Message']).sum()
print(f"\nsms_spam_collection.txt: {len(sms_txt)}, spam.csv: {len(spam_csv)}")
print(f"sms_spam_collection in spam.csv: {sms_in_spam}/{len(sms_txt)}")

# Uzbek expansion sets
sms_uz_enr = pd.read_csv('data/uzbek_expansion/sms_uz_enriched.csv', encoding='utf-8', on_bad_lines='skip')
tel_uz_enr = pd.read_csv('data/uzbek_expansion/telegram_uz_enriched.csv', encoding='utf-8', on_bad_lines='skip')
uz_v2 = pd.read_csv('data/uzbek_expansion/uz_dataset_v2.csv', encoding='utf-8', on_bad_lines='skip')
trans = pd.read_csv('data/uzbek_expansion/translation_candidates.csv', encoding='utf-8', on_bad_lines='skip')

print(f"\nUzbek expansion files:")
print(f"  sms_uz_enriched: {len(sms_uz_enr)}")
print(f"  telegram_uz_enriched: {len(tel_uz_enr)}")
print(f"  uz_dataset_v2: {len(uz_v2)}")
print(f"  translation_candidates: {len(trans)}")

# Check phishing/security
fin = pd.read_csv('data/phishing_expansion/uz_financial_scams.csv', encoding='utf-8', on_bad_lines='skip')
sec = pd.read_csv('data/security_alert_expansion/uz_security_alert_scams.csv', encoding='utf-8', on_bad_lines='skip')
fin_in_v7 = fin['text'].isin(v7['text']).sum()
sec_in_v7 = sec['text'].isin(v7['text']).sum()
print(f"\nPhishing/Security expansion:")
print(f"  uz_financial_scams: {len(fin)}, in master_v7: {fin_in_v7}")
print(f"  uz_security_alert_scams: {len(sec)}, in master_v7: {sec_in_v7}")

# Sources overlap with messages_cleaned
sms_uz = pd.read_csv('data/sources/sms_uz.csv', encoding='utf-8', on_bad_lines='skip')
sms_uz_clean = pd.read_csv('data/sources/sms_uz_clean.csv', encoding='utf-8', on_bad_lines='skip')
tel_ham = pd.read_csv('data/sources/telegram_ham_samples.csv', encoding='utf-8', on_bad_lines='skip')
tel_spam = pd.read_csv('data/sources/telegram_spam_cleaned.csv', encoding='utf-8', on_bad_lines='skip')

mc1 = pd.read_csv('data/processed/messages_cleaned.csv', encoding='utf-8', on_bad_lines='skip')
sms_uz_in_mc = sms_uz['text'].isin(mc1['text']).sum()
tel_ham_in_mc = tel_ham['text'].isin(mc1['text']).sum()
tel_spam_in_mc = tel_spam['text'].isin(mc1['text']).sum()
sms_txt_in_mc = sms_txt['text'].isin(mc1['text']).sum()
print(f"\nSource overlaps with messages_cleaned v1:")
print(f"  sms_uz in mc: {sms_uz_in_mc}/{len(sms_uz)}")
print(f"  telegram_ham in mc: {tel_ham_in_mc}/{len(tel_ham)}")
print(f"  telegram_spam in mc: {tel_spam_in_mc}/{len(tel_spam)}")
print(f"  sms_spam_collection in mc: {sms_txt_in_mc}/{len(sms_txt)}")
