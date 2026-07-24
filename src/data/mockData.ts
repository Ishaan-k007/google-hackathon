import { FoodOffer, FoodBankPartner, StoreAccount } from '../types';

export const INITIAL_STORE_ACCOUNTS: StoreAccount[] = [
  {
    id: 'store-1',
    name: 'Tesco - Kings Cross',
    address: '21 Caledonian Rd, London N1 9DX',
    dockInfo: 'Loading Bay 02 (Rear Service Yard)',
    defaultCodePrefix: 'TESCO-KGX',
  },
  {
    id: 'store-2',
    name: 'Tesco Express - Covent Garden',
    address: '22 Floral Street, London WC2E 9DS',
    dockInfo: 'Delivery Dock A (Service Lane)',
    defaultCodePrefix: 'TESCO-COV',
  },
  {
    id: 'store-3',
    name: 'Tesco Superstore - Kensington',
    address: '224-226 Kensington High Street, London W8 7RG',
    dockInfo: 'Goods In Yard (Bay 3)',
    defaultCodePrefix: 'TESCO-KEN',
  },
  {
    id: 'store-4',
    name: 'Tesco Metro - Camden Town',
    address: '122 Camden High Street, London NW1 0LU',
    dockInfo: 'Rear Service Entrance',
    defaultCodePrefix: 'TESCO-CAM',
  },
];

export const INITIAL_FOOD_BANKS: FoodBankPartner[] = [
  { id: 'fb-1', name: 'St. Mary Community Food Pantry', location: '42 High Street, Central District' },
  { id: 'fb-2', name: 'Hope Haven Shelter & Kitchen', location: '108 Victoria Road, North Suburbs' },
  { id: 'fb-3', name: 'Green Leaf Youth & Family Hub', location: '15 Meadow Lane, Westside' },
  { id: 'fb-4', name: 'City Rescue Mission', location: '88 Commerce Way, Downtown' },
];

export const INITIAL_FOOD_OFFERS: FoodOffer[] = [
  {
    id: 'OFFER-101',
    storeName: 'Tesco - Kings Cross',
    pickupLocation: '21 Caledonian Rd, London N1 9DX (Loading Bay 02)',
    pickupCode: 'TESCO-KGX-88',
    description: '12 crates of organic apples & pears, 15 artisan sourdough loaves baked this morning, and 8 gallons of semi-skimmed milk expiring tomorrow at 5 PM.',
    createdAt: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
    status: 'AVAILABLE',
  },
  {
    id: 'OFFER-102',
    storeName: 'Tesco - Kings Cross',
    pickupLocation: '21 Caledonian Rd, London N1 9DX (Loading Bay 02)',
    pickupCode: 'TESCO-KGX-22',
    description: '20 chilled ready-meal packages (vegetable curry & rice) and 10 boxes of fresh salad wraps. Temperature logged at 3°C.',
    createdAt: new Date(Date.now() - 1000 * 60 * 120).toISOString(),
    status: 'CLAIMED',
    claimedBy: 'St. Mary Community Food Pantry',
    claimedAt: new Date(Date.now() - 1000 * 60 * 45).toISOString(),
  },
  {
    id: 'OFFER-103',
    storeName: 'Tesco Express - Covent Garden',
    pickupLocation: '22 Floral Street, London WC2E 9DS (Delivery Dock A)',
    pickupCode: 'TESCO-COV-44',
    description: '15 crates of fresh salads, 10 loaves of sourdough bread, and 6 gallons of organic whole milk.',
    createdAt: new Date(Date.now() - 1000 * 60 * 180).toISOString(),
    status: 'AVAILABLE',
  },
  {
    id: 'OFFER-104',
    storeName: 'Tesco Metro - Camden Town',
    pickupLocation: '122 Camden High Street, London NW1 0LU (Rear Service Entrance)',
    pickupCode: 'TESCO-CAM-19',
    description: '18 crates of bakery items, 30 liters of milk, and 12 packs of fresh mozzarella cheese.',
    createdAt: new Date(Date.now() - 1000 * 60 * 360).toISOString(),
    status: 'COLLECTED',
    claimedBy: 'Hope Haven Shelter & Kitchen',
    claimedAt: new Date(Date.now() - 1000 * 60 * 200).toISOString(),
    collectedAt: new Date(Date.now() - 1000 * 60 * 60).toISOString(),
  },
];


