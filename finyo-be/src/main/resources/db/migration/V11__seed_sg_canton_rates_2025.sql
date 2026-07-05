-- Canton St. Gallen (SG) cantonal simple tax (einfache Staatssteuer) 2025
-- Tariff A = single persons
-- Tariff B = married persons / widowed with children
-- rate_per_100 = tax rate in CHF per 100 CHF of taxable income above income_from
-- The cantonal multiplier (Steuerfuss) for SG state tax is 100% (stored separately per commune)

INSERT INTO tax_canton_rate (tax_year, canton_code, tariff, income_from, income_to, base_tax, rate_per_100) VALUES
-- SG Tariff A (single)
(2025, 'SG', 'A',       0.00,   10000.00,     0.00, 0.000000),
(2025, 'SG', 'A',   10000.00,   20000.00,    50.00, 0.500000),
(2025, 'SG', 'A',   20000.00,   30000.00,   150.00, 1.500000),
(2025, 'SG', 'A',   30000.00,   50000.00,   300.00, 2.000000),
(2025, 'SG', 'A',   50000.00,  100000.00,   700.00, 3.500000),
(2025, 'SG', 'A',  100000.00,  200000.00,  2450.00, 6.500000),
(2025, 'SG', 'A',  200000.00,  300000.00,  8950.00, 8.000000),
(2025, 'SG', 'A',  300000.00,        NULL, 16950.00, 8.500000),

-- SG Tariff B (married / widowed with children)
(2025, 'SG', 'B',       0.00,   20000.00,     0.00, 0.000000),
(2025, 'SG', 'B',   20000.00,   40000.00,   100.00, 0.500000),
(2025, 'SG', 'B',   40000.00,   60000.00,   200.00, 1.500000),
(2025, 'SG', 'B',   60000.00,   80000.00,   500.00, 2.500000),
(2025, 'SG', 'B',   80000.00,  120000.00,  1000.00, 3.500000),
(2025, 'SG', 'B',  120000.00,  250000.00,  2400.00, 6.000000),
(2025, 'SG', 'B',  250000.00,  350000.00, 10200.00, 7.500000),
(2025, 'SG', 'B',  350000.00,        NULL, 17700.00, 8.500000);

-- SG commune tax multipliers (Steuerfuss) for 2025
-- multiplier stored as decimal: 114% -> 1.14, 88% -> 0.88
-- BFS numbers are official Swiss Federal Statistical Office commune identifiers

INSERT INTO tax_commune_multiplier (tax_year, canton_code, commune_name, bfs_number, multiplier) VALUES
(2025, 'SG', 'St. Gallen',        3203, 1.1400),
(2025, 'SG', 'Rapperswil-Jona',   3340, 0.8800),
(2025, 'SG', 'Gossau SG',         3213, 1.1200),
(2025, 'SG', 'Wittenbach',        3232, 1.0400),
(2025, 'SG', 'Gaiserwald',        3212, 0.9700),
(2025, 'SG', 'Flawil',            3214, 1.0900),
(2025, 'SG', 'Wil SG',            3427, 1.0600),
(2025, 'SG', 'Rorschach',         3221, 1.2400),
(2025, 'SG', 'Buchs SG',          3401, 0.9900),
(2025, 'SG', 'Sargans',           3342, 1.0400),
(2025, 'SG', 'Uznach',            3351, 1.1200),
(2025, 'SG', 'Altstätten',        3301, 1.0300),
(2025, 'SG', 'Walenstadt',        3344, 1.0700),
(2025, 'SG', 'Diepoldsau',        3308, 1.0800),
(2025, 'SG', 'Goldach',           3215, 0.9900),
(2025, 'SG', 'Mörschwil',         3218, 0.9400),
(2025, 'SG', 'Steinach',          3226, 1.0200);
