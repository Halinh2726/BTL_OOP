INSERT INTO vehicle_types (code, name)
SELECT 'XE_MAY_DIEN', old_type.name
FROM vehicle_types old_type
WHERE old_type.code = 'XE_DAP_DIEN'
  AND NOT EXISTS (SELECT 1 FROM vehicle_types new_type WHERE new_type.code = 'XE_MAY_DIEN');

INSERT INTO pass_prices (vehicle_type_code, monthly_points)
SELECT 'XE_MAY_DIEN', old_price.monthly_points
FROM pass_prices old_price
WHERE old_price.vehicle_type_code = 'XE_DAP_DIEN'
  AND NOT EXISTS (SELECT 1 FROM pass_prices new_price WHERE new_price.vehicle_type_code = 'XE_MAY_DIEN');

UPDATE vehicles
SET vehicle_type_code = 'XE_MAY_DIEN'
WHERE vehicle_type_code = 'XE_DAP_DIEN';

DELETE FROM pass_prices WHERE vehicle_type_code = 'XE_DAP_DIEN';
DELETE FROM vehicle_types WHERE code = 'XE_DAP_DIEN';
