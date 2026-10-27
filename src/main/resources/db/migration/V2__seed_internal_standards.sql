-- Fictional sample company profile for the demo. Thresholds are set so the three sample tenders
-- produce a mix of COMPLIANT / NON_COMPLIANT verdicts rather than all green.
INSERT INTO internal_compliance_standards
    (standard_code, category, comparator, numeric_threshold, unit, allowed_values, standards_version)
VALUES
    ('STD_MIN_ANNUAL_TURNOVER', 'FINANCIAL_TURNOVER', 'MIN_REQUIRED', 3.00, 'INR_CRORE',
     '{"description": "Average annual turnover over last 3 financial years (sample company)"}'::jsonb, 1),
    ('STD_MIN_NET_WORTH', 'NET_WORTH', 'MIN_REQUIRED', 1.00, 'INR_CRORE',
     '{"description": "Positive net worth, sample company"}'::jsonb, 1),
    ('STD_MAX_EMD_LIMIT', 'EARNEST_MONEY_DEPOSIT', 'MAX_ALLOWED', 5.00, 'INR_LAKH',
     '{"description": "Maximum single-bid EMD approved without board sign-off"}'::jsonb, 1),
    ('STD_ACTIVE_ISO_CERTS', 'ISO_CERTIFICATION', 'REQUIRED_SET', NULL, 'BOOLEAN',
     '["ISO 9001:2015", "ISO/IEC 27001:2022"]'::jsonb, 1),
    ('STD_PSU_GOVT_EXPERIENCE', 'PAST_EXPERIENCE', 'MIN_REQUIRED', 3.00, 'YEARS',
     '{"description": "Completed central/state government software projects (sample company)"}'::jsonb, 1),
    ('STD_MAX_LIQUIDATED_DAMAGES', 'LIQUIDATED_DAMAGES', 'MAX_ALLOWED', 10.00, 'PERCENT',
     '{"description": "Maximum acceptable LD cap as a share of contract value"}'::jsonb, 1);
