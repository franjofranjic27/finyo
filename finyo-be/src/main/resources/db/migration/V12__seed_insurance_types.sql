-- Seed reference data for Swiss insurance types
-- typical_cost_min / typical_cost_max are annual amounts in CHF unless noted
-- Costs for monthly-billed insurances (KRANKENKASSE, ZUSATZVERSICHERUNG) are stored as annual equivalents

INSERT INTO insurance_type (
    code,
    name_en,
    name_de,
    description_en,
    description_de,
    mandatory,
    recommended,
    typical_cost_min,
    typical_cost_max,
    comparison_url,
    sort_order
) VALUES

(
    'KRANKENKASSE',
    'Basic Health Insurance (KVG)',
    'Grundversicherung (KVG)',
    'Mandatory health insurance covering essential medical treatments, hospital stays, and medications as defined by Swiss law. Every resident in Switzerland must hold a policy with a recognised health insurer (Krankenkasse). Premiums vary by insurer, canton, and chosen deductible (Franchise).',
    'Die obligatorische Grundversicherung nach dem Krankenversicherungsgesetz (KVG) deckt alle medizinisch notwendigen Behandlungen, Spitalaufenthalte und Medikamente. Jede in der Schweiz wohnhafte Person ist verpflichtet, sich bei einer anerkannten Krankenkasse zu versichern. Die Prämien variieren je nach Kasse, Kanton und gewählter Franchise.',
    TRUE,
    TRUE,
    3600.00,
    7200.00,
    'https://www.priminfo.admin.ch',
    1
),

(
    'ZUSATZVERSICHERUNG',
    'Supplemental Health Insurance',
    'Zusatzversicherung (VVG)',
    'Voluntary supplemental health insurance governed by private law (VVG) that extends coverage beyond the mandatory basic plan. Common options include semi-private or private hospital wards, alternative medicine, dental care, and vision. Useful for those who want more comfort or broader coverage.',
    'Freiwillige Zusatzversicherungen nach dem Versicherungsvertragsgesetz (VVG) ergänzen die Grundversicherung um Leistungen wie Halbprivat- oder Privatabteilung im Spital, Komplementärmedizin, Zahnbehandlungen und Brillen. Sie bieten mehr Komfort und Wahlfreiheit bei Arzt und Spital.',
    FALSE,
    TRUE,
    600.00,
    3600.00,
    'https://www.comparis.ch/krankenkassen',
    2
),

(
    'HAFTPFLICHT',
    'Personal Liability Insurance',
    'Privathaftpflichtversicherung',
    'Covers financial claims made against you by third parties for accidental damage to property or personal injury caused in your private life. Without this insurance, a single accident could result in significant out-of-pocket costs. Strongly recommended for all residents in Switzerland.',
    'Die Privathaftpflichtversicherung schützt Sie vor finanziellen Forderungen Dritter, wenn Sie im Privatleben versehentlich Sach- oder Personenschäden verursachen. Ohne diese Versicherung können selbst kleine Missgeschicke zu hohen Kosten führen. Sie gilt als eine der wichtigsten Versicherungen in der Schweiz.',
    FALSE,
    TRUE,
    100.00,
    200.00,
    'https://www.comparis.ch/haftpflicht',
    3
),

(
    'HAUSRAT',
    'Household Contents Insurance',
    'Hausratversicherung',
    'Protects the contents of your home — furniture, electronics, clothing, and other personal belongings — against damage or loss from fire, water, theft, and natural hazards. It complements building insurance and is especially important for renters who are not covered by the landlord''s policy.',
    'Die Hausratversicherung schützt Ihren Hausrat — Möbel, Elektronik, Kleidung und persönliche Gegenstände — vor Schäden durch Brand, Wasser, Diebstahl und Naturereignisse. Sie ergänzt die Gebäudeversicherung und ist besonders für Mieterinnen und Mieter wichtig, da deren Eigentum nicht durch die Versicherung des Vermieters gedeckt ist.',
    FALSE,
    TRUE,
    150.00,
    400.00,
    'https://www.comparis.ch/hausrat',
    4
),

(
    'LEBEN',
    'Life Insurance / Death Risk',
    'Lebensversicherung / Todesfallrisiko',
    'Provides a lump-sum payment to named beneficiaries in the event of the policyholder''s death. Particularly valuable for people with dependants, a mortgage, or business obligations. It ensures loved ones are financially protected when the primary earner is no longer able to provide.',
    'Die Todesfallversicherung zahlt im Todesfall des Versicherten eine vereinbarte Summe an die Begünstigten aus. Sie ist besonders wichtig für Personen mit Angehörigen, einer Hypothek oder geschäftlichen Verpflichtungen. Sie stellt sicher, dass Hinterbliebene finanziell abgesichert sind.',
    FALSE,
    FALSE,
    200.00,
    1000.00,
    NULL,
    5
),

(
    'ERWERBSUNFAEHIGKEIT',
    'Disability Income Insurance',
    'Erwerbsunfähigkeitsversicherung',
    'Replaces a portion of your income if you become unable to work due to illness or accident. The Swiss social security system (IV/AHV) and occupational pension (BVG) typically cover only a fraction of your last salary, making private disability cover important to maintain your standard of living.',
    'Die Erwerbsunfähigkeitsversicherung ersetzt einen Teil Ihres Einkommens, wenn Sie durch Krankheit oder Unfall nicht mehr arbeiten können. Da die staatliche Invalidenversicherung (IV) und die Pensionskasse (BVG) oft nur einen Bruchteil des letzten Lohns abdecken, ist eine private Absicherung wichtig, um den Lebensstandard zu halten.',
    FALSE,
    TRUE,
    500.00,
    2000.00,
    NULL,
    6
),

(
    'RECHTSSCHUTZ',
    'Legal Protection Insurance',
    'Rechtsschutzversicherung',
    'Covers legal costs — including lawyer fees, court costs, and expert opinions — when you need to assert or defend your rights in disputes. Common use cases include employment disputes, tenancy conflicts, traffic incidents, and consumer protection matters.',
    'Die Rechtsschutzversicherung übernimmt Anwalts-, Gerichts- und Gutachterkosten, wenn Sie Ihre Rechte durchsetzen oder verteidigen müssen. Typische Einsatzbereiche sind Arbeitsstreitigkeiten, Mietkonflikte, Verkehrsunfälle und Konsumentenstreitigkeiten.',
    FALSE,
    FALSE,
    200.00,
    500.00,
    'https://www.comparis.ch/rechtsschutz',
    7
),

(
    'REISE',
    'Travel Insurance',
    'Reiseversicherung',
    'Covers unexpected costs arising during travel, such as trip cancellation, medical emergencies abroad, lost luggage, and travel delays. While Swiss residents are covered by basic health insurance within Switzerland and some EU countries, coverage abroad can be limited and costly without a dedicated travel policy.',
    'Die Reiseversicherung deckt unvorhergesehene Kosten auf Reisen, wie Reiseabbruch, medizinische Notfälle im Ausland, Gepäckverlust und Reiseverspätungen. Auch wenn die Schweizer Grundversicherung im Ausland teilweise gilt, können die Kosten ohne separate Reiseversicherung schnell hoch werden.',
    FALSE,
    FALSE,
    100.00,
    300.00,
    NULL,
    8
),

(
    'AUTO',
    'Motor Vehicle Insurance',
    'Motorfahrzeugversicherung',
    'Third-party liability insurance (Haftpflicht) is mandatory for all registered motor vehicles in Switzerland. Comprehensive and partial casco (Vollkasko / Teilkasko) coverage is optional but protects against damage to your own vehicle from accidents, theft, fire, and natural events. Required for any vehicle owner.',
    'Die Motorfahrzeughaftpflichtversicherung ist für alle in der Schweiz zugelassenen Fahrzeuge gesetzlich vorgeschrieben. Die Vollkasko- und Teilkaskoversicherung sind freiwillig, schützen aber das eigene Fahrzeug bei Unfällen, Diebstahl, Brand und Naturereignissen. Für alle Fahrzeughalter obligatorisch.',
    FALSE,
    FALSE,
    500.00,
    1500.00,
    'https://www.comparis.ch/auto',
    9
),

(
    'BVG',
    'Occupational Pension (BVG)',
    'Berufliche Vorsorge (BVG/Pensionskasse)',
    'The second pillar of the Swiss pension system, the BVG (Berufliche Vorsorge) is mandatory for employed persons earning above the entry threshold. Contributions are split between employer and employee and accumulate as retirement capital. It supplements AHV/IV benefits and is managed by the employer''s pension fund (Pensionskasse).',
    'Die berufliche Vorsorge nach BVG ist der zweite Pfeiler des Schweizer Vorsorgesystems und für Arbeitnehmende ab einem bestimmten Jahreslohn obligatorisch. Beiträge werden je zur Hälfte von Arbeitgeber und Arbeitnehmer bezahlt und bilden das Altersguthaben. Die BVG ergänzt die AHV/IV-Leistungen und wird über die Pensionskasse des Arbeitgebers abgewickelt.',
    TRUE,
    TRUE,
    NULL,
    NULL,
    NULL,
    10
);
