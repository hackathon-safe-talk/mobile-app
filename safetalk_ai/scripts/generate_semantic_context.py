import pandas as pd
import random
import os
from pathlib import Path

# Setup
random.seed(42)
BASE_DIR = Path(__file__).resolve().parents[1]
OUT_DIR = BASE_DIR / "data" / "expansions"
OUT_FILE = OUT_DIR / "safetalk_semantic_context_v1.csv"

# Make sure directory exists
OUT_DIR.mkdir(parents=True, exist_ok=True)

# ─────────────────────────────────────────────────────────────
# 1. Combinatorial Components
# ─────────────────────────────────────────────────────────────

COMPONENTS = {
    'EN': {
        'greeting': ["Hi,", "Hello,", "Hey mate,", "Yo,", "Dear user,", "URGENT:", "URGENT -", "Notice:", "[ALERT]", ""],
        'punctuation': [".", "!", "?", "...", "!!", " ! ", ""],
        'noise': ["", " tbh", " ngl", " ASAP", " plz", " asap!", " dm me"],
        'scam_link': ["http://scam.uz", "http://crypto-double.com", "bit.ly/123", "t.me/fake_channel", "[link]"],
    },
    'UZ': {
        'greeting': ["Salom,", "Assalomu alaykum,", "Qalay,", "Hurmatli mijoz,", "DIQQAT:", "Ogohlantirish:", "Muhim:", ""],
        'punctuation': [".", "!", "?", "...", "!!", " ! ", ""],
        'noise': ["", " brat", " uka", " tezroq", " iltimos", " dm", " yozvor"],
        'scam_link': ["http://scam.uz", "http://kripto-foyda.uz", "t.me/yopiq_guruh", "bit.ly/uzb_link", "[havola]"],
    },
    'RU': {
        'greeting': ["Привет,", "Здравствуйте,", "Здарова,", "Уважаемый клиент,", "ВНИМАНИЕ:", "Срочно:", ""],
        'punctuation': [".", "!", "?", "...", "!!", " ! ", ""],
        'noise': ["", " пжл", " срочно", " в лс", " бро", " брат", ""],
        'scam_link': ["http://scam.ru", "t.me/super_vzlom", "bit.ly/rus_skam", "http://zarabotok.ru", "[ссылка]"],
    }
}

# ─────────────────────────────────────────────────────────────
# 2. Template Definitions (Context-Based)
# ─────────────────────────────────────────────────────────────

TEMPLATES = {
    'GAMBLING': {
        'SAFE': {
            'EN': [
                "I need to discuss the stock market group meeting today",
                "The local football team won the match today against the rivals",
                "Have you read the news about the new state casino regulations",
                "Investing in the long-term stock portfolio is a safe bet for retirement",
                "We had a small group wager on the chess tournament but it was just for fun"
            ],
            'UZ': [
                "Bugun moliya bozori haqida guruh uchrashuvimiz bor",
                "Mahalliy futbol jamoasi kechagi o'yinda yutib chiqdi",
                "Yangi davlat qoidalari haqidagi yangiliklarni eshitdingizmi",
                "Aksiya bozorida sarmoya kiritish uzoq muddatli foyda keltiradi",
                "Shaxmat o'yinida biroz garov bog'ladik, lekin bu shunchaki o'yin edi"
            ],
            'RU': [
                "Нам нужно обсудить встречу группы фондового рынка сегодня",
                "Местная футбольная команда выиграла сегодняшний матч",
                "Вы читали новости о новых правилах казино",
                "Инвестирование в акции - это надежная ставка на будущее",
                "Мы немного поспорили на шахматном турнире, но это было просто ради интереса"
            ]
        },
        'SUSPICIOUS': {
            'EN': [
                "Did you see that post about doubling crypto in a week",
                "I found a new platform for fast trades",
                "Someone told me there's a loophole to earn money fast",
                "Are you interested in a new group for making extra cash",
                "I've been lucky lately with these new games, want to see"
            ],
            'UZ': [
                "Bir haftada kriptoni ikki baravar ko'paytirish haqidagi postni ko'rdingizmi",
                "Tezkor savdolar uchun yangi platforma topdim",
                "Kimdir pulni tez ishlashning siri borligini aytdi",
                "Qo'shimcha pul ishlash guruhiga qo'shilishni xohlaysizmi",
                "Shu yangi o'yinlarda omadim keldi, sen ham ko'rasanmi"
            ],
            'RU': [
                "Ты видел тот пост об удвоении крипты за неделю",
                "Я нашел новую платформу для быстрых сделок",
                "Кто-то сказал мне, что есть лазейка, чтобы быстро заработать деньги",
                "Вам интересна новая группа для дополнительного заработка",
                "Мне везет в последнее время с этими новыми играми, хочешь посмотреть"
            ]
        },
        'DANGEROUS': {
            'EN': [
                "Guaranteed 100 percent payout today! Invest $50, get $500 in 2 hours via {scam_link}",
                "100% guaranteed profit on the next match. Buy VIP signals here {scam_link}",
                "Claim your $1000 casino bonus now, this is not a scam {scam_link}",
                "Earn money and become rich immediately with our crypto trading bot {scam_link}",
                "Betting glitch found! Use this strategy to win every time {scam_link}"
            ],
            'UZ': [
                "Bugun 100 foiz to'lov kafolatlanadi! 50$ tikib, 2 soatda 500$ oling: {scam_link}",
                "Keyingi o'yinga 100% kafolatlangan yutuq. VIP signallarni bu yerdan sotib oling: {scam_link}",
                "1000$ kazino bonusingizni hoziroq oling, bu firibgarlik emas {scam_link}",
                "Kripto savdo botimiz orqali zudlik bilan pul ishlash va boyib ketish {scam_link}",
                "O'yin xatosi topildi! Har safar yutish uchun ushbu strategiyani qo'llang {scam_link}"
            ],
            'RU': [
                "Гарантированная выплата 100 процентов сегодня! Вложи 50 долларов, получи 500 за 2 часа {scam_link}",
                "100% гарантированная прибыль на следующий матч. Купить VIP сигналы здесь {scam_link}",
                "Забери свой бонус казино на 1000 долларов сейчас, это не скам {scam_link}",
                "Зарабатывайте деньги и станьте богатым мгновенно с нашим крипто-ботом {scam_link}",
                "Найден баг в ставках! Используй эту стратегию, чтобы выигрывать каждый раз {scam_link}"
            ]
        }
    },
    'ADULT': {
        'SAFE': {
            'EN': [
                "We need to schedule the sex education session for the biology class",
                "This documentary discusses adult themes and historical context",
                "My doctor prescribed medication for a reproductive health issue",
                "The study explores human sexuality from a sociological perspective",
                "Children should be protected from inappropriate internet material"
            ],
            'UZ': [
                "Biologiya darsi uchun jinsiy tarbiya mashg'ulotini rejalashtirishimiz kerak",
                "Ushbu hujjatli film katta yoshdagilar uchun mo'ljallangan qadimiy mavzularni muhokama qiladi",
                "Shifokorim reproduktiv salomatlik muammosi uchun dori yozib berdi",
                "Tadqiqot inson jinsiyatini sotsiologik nuqtai nazardan o'rganadi",
                "Bolalarni nomaqbul internet materiallaridan himoya qilish kerak"
            ],
            'RU': [
                "Нам нужно запланировать занятие по половому воспитанию для класса биологии",
                "В этом документальном фильме обсуждаются темы для взрослых и исторический контекст",
                "Мой врач прописал лекарство для решения проблемы репродуктивного здоровья",
                "Исследование изучает человеческую сексуальность с социологической точки зрения",
                "Детей следует оградить от неприемлемых материалов в Интернете"
            ]
        },
        'SUSPICIOUS': {
            'EN': [
                "Why is this private photo circulating here",
                "Did you see the mature content they sent in the group",
                "Someone shared a weird link for 18+ content yesterday",
                "They are talking about escort services in the main chat",
                "I was surprised by the explicit topics they discussed today"
            ],
            'UZ': [
                "Nima uchun bu shaxsiy rasm bu yerda tarqalmoqda",
                "Guruhga yuborgan kattalar uchun mo'ljallangan kontentni ko'rdingizmi",
                "Kimdir kecha 18+ kontent uchun g'alati havola ulashdi",
                "Ular asosiy chatda eskort xizmatlari haqida gaplashishmoqda",
                "Bugun ochiq mavzular muhokama qilinganidan hayron qoldim"
            ],
            'RU': [
                "Почему это приватное фото распространяется здесь",
                "Ты видел контент для взрослых, который они прислали в группу",
                "Кто-то поделился странной ссылкой на контент 18+ вчера",
                "Они обсуждают эскорт-услуги в главном чате",
                "Я был удивлен откровенными темами, которые они обсуждали сегодня"
            ]
        },
        'DANGEROUS': {
            'EN': [
                "Join the hidden VIP channel for exclusive 18+ leaked content tonight {scam_link}",
                "Download full sex videos without registration click here {scam_link}",
                "Meet local girls tonight for private escort services {scam_link}",
                "Watch the leaked private tape now before it gets deleted {scam_link}",
                "Uncensored adult videos and free nude pictures {scam_link}"
            ],
            'UZ': [
                "Bugun tunda maxsus 18+ sizdirilgan videolarni ko'rish uchun yopiq VIP guruhiga qo'shiling {scam_link}",
                "Ro'yxatdan o'tmasdan to'liq porno videolarni yuklab oling, bu yerni bosing {scam_link}",
                "Bugun kechqurun xususiy eskort xizmatlari uchun mahalliy qizlar bilan tanishing {scam_link}",
                "O'chirilmasidan oldin sizdirilgan xususiy videoni hoziroq ko'ring {scam_link}",
                "Senzurasiz kattalar uchun videolar va bepul yalang'och rasmlar {scam_link}"
            ],
            'RU': [
                "Присоединяйтесь к скрытому VIP-каналу, чтобы получить эксклюзивный слитый контент 18+ сегодня ночью {scam_link}",
                "Скачивайте полные порно видео без регистрации, нажмите здесь {scam_link}",
                "Познакомьтесь с местными девушками сегодня вечером для частных эскорт-услуг {scam_link}",
                "Смотри слитое видео сейчас, пока его не удалили {scam_link}",
                "Видео для взрослых без цензуры и бесплатные голые фото {scam_link}"
            ]
        }
    },
    'EXTREMISM': {
        'SAFE': {
            'EN': [
                "Friday prayers will be held at the main mosque at 1:00 PM inshallah",
                "I am attending Islamic classes to study the Quran better",
                "May God grant peace and blessings to you and your family",
                "We must practice patience and kindness as our religion teaches us",
                "The religious group is organizing a charity event for orphans"
            ],
            'UZ': [
                "Juma namozi InshaAlloh soat 13:00 da markaziy masjidda o'qiladi",
                "Qur'onni yaxshiroq o'rganish uchun islomiy darslarga qatnashyapman",
                "Alloh sizga va oilangizga tinchlik va baraka bersin",
                "Dinimiz o'rgatganidek, sabr va ruxsozni ko'paytirishimiz kerak",
                "Diniy jamoa yetimlar uchun xayriya tadbirini uyushtirmoqda"
            ],
            'RU': [
                "Пятничная молитва состоится в главной мечети в 13:00 иншалла",
                "Я посещаю исламские занятия, чтобы лучше изучать Коран",
                "Пусть Аллах дарует мир и благословение вам и вашей семье",
                "Мы должны проявлять терпение и доброту, как учит нас наша религия",
                "Религиозная группа организует благотворительное мероприятие для сирот"
            ]
        },
        'SUSPICIOUS': {
            'EN': [
                "Have you read the new interpretation posted by that controversial scholar",
                "I think the group is becoming too radical with their beliefs",
                "Some people believe that strict measures are necessary to cleanse the society",
                "That channel is sharing ideas that oppose our current laws",
                "Who distributed those strange pamphlets near the mosque today"
            ],
            'UZ': [
                "O'sha tortishuvli domla haqidagi yangi talqinni o'qib chiqdingizmi",
                "Mening fikrimcha, guruh o'z e'tiqodlarida juda radikallashib bormoqda",
                "Ba'zilar jamiyatni tozalash uchun qattiq choralar ko'rish zarur deb biladi",
                "Ushbu kanal hozirgi qonunlarimizga zid g'oyalarni tarqatmoqda",
                "Bugun masjid yaqinida o'sha g'alati kitobchalarni kim tarqatdi"
            ],
            'RU': [
                "Вы читали новую интерпретацию, опубликованную тем скандальным ученым",
                "Я думаю, что группа становится слишком радикальной в своих убеждениях",
                "Некоторые считают, что для очищения общества необходимы жесткие меры",
                "Этот канал делится идеями, которые противоречат нашим текущим законам",
                "Кто сегодня раздавал эти странные брошюры возле мечети"
            ]
        },
        'DANGEROUS': {
            'EN': [
                "Brothers, the time to fight and take action against the infidels is now! Rise up",
                "We are recruiting soldiers for the holy war. Join our secret channel {scam_link}",
                "Support the revolution by sending anonymous donations to this wallet {scam_link}",
                "They deserve violence for what they have done. It is time for jihad",
                "Learn how to build homemade explosives to defend our faith from the enemies"
            ],
            'UZ': [
                "Birodarlar, kofirlarga qarshi kurashish va harakat qilish vaqti keldi! Qo'zg'alish kerak",
                "Biz muqaddas urush uchun askarlarni jalb qilmoqdamiz. Yashirin guruhimizga qo'shiling {scam_link}",
                "Inqilobni qo'llab-quvvatlash uchun ushbu hamyonga pul yuboring {scam_link}",
                "Ular qilgan ishlari uchun o'limga mahkumdir. Jihod vaqti keldi",
                "Dushmanlardan o'zimizni himoya qilish uchun qo'lbola portlovchi moddalar yasashni o'rganing {scam_link}"
            ],
            'RU': [
                "Братья, время бороться и действовать против неверных пришло! Восстаньте",
                "Мы вербуем солдат для священной войны. Присоединяйся к нашему тайному каналу {scam_link}",
                "Поддержите революцию, отправив анонимные пожертвования на этот кошелек {scam_link}",
                "Они заслуживают насилия за то, что они сделали. Пришло время джихада",
                "Узнайте, как создать самодельную взрывчатку, чтобы защитить нашу веру от врагов {scam_link}"
            ]
        }
    }
}

# ─────────────────────────────────────────────────────────────
# 3. Generation Engine
# ─────────────────────────────────────────────────────────────

def generate_message(category, label, lang):
    comps = COMPONENTS[lang]
    base_templates = TEMPLATES[category][label][lang]
    
    # 1. Base selection
    base = random.choice(base_templates)
    
    # 2. Add greeting randomly (40% chance)
    if random.random() < 0.4:
        greeting = random.choice(comps['greeting'])
        if greeting:
            base = f"{greeting} {base.lower()}"
            
    # 3. Add link randomly to DANGEROUS or let format strings handle it
    link = random.choice(comps['scam_link'])
    if "{scam_link}" in base:
        base = base.replace("{scam_link}", link)
    elif label == 'DANGEROUS' and random.random() < 0.3:
        base = f"{base} {link}"
        
    # 4. Add punctuation
    base = f"{base}{random.choice(comps['punctuation'])}"
    
    # 5. Add noise/slang randomly (20% chance)
    if random.random() < 0.2:
        base = f"{base}{random.choice(comps['noise'])}"
        
    # 6. Rare Typos / Casing tweaks
    if random.random() < 0.1:
        base = base.lower()
    
    return base.strip()

def generate_dataset(target_rows):
    data = []
    generated_texts = set()
    
    # We want a balanced distribution overall, but specific language targets
    lang_weights = {'UZ': 0.50, 'EN': 0.30, 'RU': 0.20}
    categories = ['GAMBLING', 'ADULT', 'EXTREMISM']
    labels = ['SAFE', 'SUSPICIOUS', 'DANGEROUS']
    
    print(f"Targeting {target_rows} rows...")
    attempts = 0
    max_attempts = target_rows * 50
    
    while len(data) < target_rows and attempts < max_attempts:
        attempts += 1
        
        # Pick dimension based on weights
        r = random.random()
        if r < 0.50:
            lang = 'UZ'
        elif r < 0.80:
            lang = 'EN'
        else:
            lang = 'RU'
            
        cat = random.choice(categories)
        label = random.choice(labels)
        
        text = generate_message(cat, label, lang)
        
        if text not in generated_texts:
            generated_texts.add(text)
            data.append({
                'text': text,
                'label': label,
                'category': cat,
                'language': lang.lower(),
                'source': 'semantic_context_v1'
            })
            
    if attempts >= max_attempts:
        print("[WARN] Combinatorial exhaustion reached.")
        
    df = pd.DataFrame(data)
    return df

# ─────────────────────────────────────────────────────────────
# 4. Main Execution
# ─────────────────────────────────────────────────────────────

def main():
    target = 4500 # Generate a solid 4500 high quality semantic context texts
    df = generate_dataset(target)
    
    print("==================================================")
    print(" Semantic Context Generator ")
    print("==================================================")
    
    print(f"Total Unique Rows: {len(df):,}")
    
    print("\n[Categories]")
    print(df['category'].value_counts())
    
    print("\n[Labels]")
    print(df['label'].value_counts())
    
    print("\n[Languages]")
    print(df['language'].value_counts())
    
    # Shuffle finally
    df = df.sample(frac=1, random_state=42).reset_index(drop=True)
    
    # Save Output
    df.to_csv(OUT_FILE, index=False, encoding='utf-8')
    print(f"\n✅ Dataset perfectly saved to: {OUT_FILE.relative_to(BASE_DIR)}")

if __name__ == "__main__":
    main()
