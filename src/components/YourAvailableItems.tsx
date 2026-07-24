import React, { useState } from 'react';
import { MapPin, KeyRound, Clock, PlusCircle, CheckCircle2, Hand, Search, AlertCircle } from 'lucide-react';
import { FoodOffer, StoreAccount } from '../types';

interface YourAvailableItemsProps {
  currentStore: StoreAccount;
  offers: FoodOffer[];
  onNavigateToFlag: () => void;
}

export const YourAvailableItems: React.FC<YourAvailableItemsProps> = ({
  currentStore,
  offers,
  onNavigateToFlag,
}) => {
  const [filter, setFilter] = useState<'ALL' | 'AVAILABLE' | 'CLAIMED' | 'COLLECTED'>('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [storeOnlyFilter, setStoreOnlyFilter] = useState(true);

  // Filter offers
  const storeOffers = offers.filter((offer) => {
    if (storeOnlyFilter && offer.storeName !== currentStore.name) {
      return false;
    }
    if (filter !== 'ALL' && offer.status !== filter) {
      return false;
    }
    if (searchTerm.trim()) {
      const term = searchTerm.toLowerCase();
      return (
        offer.description.toLowerCase().includes(term) ||
        offer.pickupCode.toLowerCase().includes(term) ||
        offer.pickupLocation.toLowerCase().includes(term)
      );
    }
    return true;
  });

  const currentStoreAvailableCount = offers.filter((o) => o.storeName === currentStore.name && o.status === 'AVAILABLE').length;

  return (
    <div className="bg-white rounded-lg border border-slate-200 p-6 shadow-2xs space-y-6">
      
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-slate-100">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-base sm:text-lg font-bold text-slate-900">Your Available Items</h2>
            <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              {currentStoreAvailableCount} Available
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            Monitor surplus batches flagged for charity food banks and track pickup status in real time.
          </p>
        </div>

        <button
          onClick={onNavigateToFlag}
          className="px-3.5 py-2 bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs rounded-md shadow-2xs transition-all flex items-center justify-center gap-2 shrink-0"
        >
          <PlusCircle className="w-4 h-4" />
          <span>Flag New Surplus Batch</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        
        {/* Search Input */}
        <div className="relative flex-grow max-w-md">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            placeholder="Search items by description, code, or location..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-3 py-2 bg-slate-50 border border-slate-200 rounded-md text-xs font-medium text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800"
          />
        </div>

        {/* Filter Pills & Store Toggle */}
        <div className="flex flex-wrap items-center gap-2">
          
          <button
            onClick={() => setStoreOnlyFilter(!storeOnlyFilter)}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold border transition-all ${
              storeOnlyFilter
                ? 'bg-slate-100 text-slate-900 border-slate-300 font-bold'
                : 'bg-slate-50 text-slate-600 border-slate-200'
            }`}
          >
            {storeOnlyFilter ? `Showing ${currentStore.name} Only` : 'Showing All Network Stores'}
          </button>

          <div className="flex items-center gap-1 bg-slate-100 p-1 rounded-md border border-slate-200">
            <button
              onClick={() => setFilter('ALL')}
              className={`px-2.5 py-1 rounded text-xs font-semibold transition-all ${
                filter === 'ALL' ? 'bg-white text-slate-900 shadow-2xs font-bold' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              All
            </button>
            <button
              onClick={() => setFilter('AVAILABLE')}
              className={`px-2.5 py-1 rounded text-xs font-semibold transition-all ${
                filter === 'AVAILABLE' ? 'bg-emerald-700 text-white shadow-2xs font-bold' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Available
            </button>
            <button
              onClick={() => setFilter('CLAIMED')}
              className={`px-2.5 py-1 rounded text-xs font-semibold transition-all ${
                filter === 'CLAIMED' ? 'bg-amber-600 text-white shadow-2xs font-bold' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Claimed
            </button>
            <button
              onClick={() => setFilter('COLLECTED')}
              className={`px-2.5 py-1 rounded text-xs font-semibold transition-all ${
                filter === 'COLLECTED' ? 'bg-slate-700 text-white shadow-2xs font-bold' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Collected
            </button>
          </div>

        </div>

      </div>

      {/* Items List */}
      {storeOffers.length === 0 ? (
        <div className="py-12 text-center border border-dashed border-slate-200 rounded-lg p-6 space-y-3">
          <AlertCircle className="w-8 h-8 text-slate-300 mx-auto" />
          <p className="text-sm font-semibold text-slate-700">No surplus items found for this filter.</p>
          <p className="text-xs text-slate-400 max-w-md mx-auto">
            {storeOnlyFilter
              ? `No items posted by ${currentStore.name} match your criteria.`
              : 'No surplus food items found in the system.'}
          </p>
          <button
            onClick={onNavigateToFlag}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold rounded-md transition-all mt-2"
          >
            <PlusCircle className="w-4 h-4" />
            <span>Flag Food Surplus Now</span>
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {storeOffers.map((offer) => {
            const isAvailable = offer.status === 'AVAILABLE';
            const isClaimed = offer.status === 'CLAIMED';
            const isCollected = offer.status === 'COLLECTED';

            return (
              <div
                key={offer.id}
                className="p-4 rounded-lg border border-slate-200 bg-slate-50/50 hover:bg-white hover:border-slate-300 transition-all space-y-3"
              >
                {/* Top Info Bar */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-200/80 pb-2.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-xs font-bold text-slate-900 bg-white px-2.5 py-1 rounded border border-slate-200">
                      {offer.storeName}
                    </span>
                    <span className="text-xs font-mono font-semibold px-2.5 py-1 rounded bg-slate-100 text-slate-800 border border-slate-200 flex items-center gap-1">
                      <KeyRound className="w-3 h-3 text-slate-500" />
                      Code: {offer.pickupCode}
                    </span>
                  </div>

                  {/* Status Badge */}
                  <div>
                    {isAvailable && (
                      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                        Available for Claim
                      </span>
                    )}
                    {isClaimed && (
                      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-[11px] font-semibold bg-amber-50 text-amber-800 border border-amber-200">
                        <Hand className="w-3 h-3 text-amber-600" />
                        Claimed by {offer.claimedBy}
                      </span>
                    )}
                    {isCollected && (
                      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">
                        <CheckCircle2 className="w-3 h-3 text-slate-600" />
                        Collected & Verified by {offer.claimedBy}
                      </span>
                    )}
                  </div>
                </div>

                {/* Food Description */}
                <p className="text-xs text-slate-800 font-medium leading-relaxed bg-white p-3 rounded-md border border-slate-200">
                  {offer.description}
                </p>

                {/* Meta details: Location & Timestamps */}
                <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500 pt-0.5">
                  <span className="flex items-center gap-1 font-medium text-slate-700">
                    <MapPin className="w-3.5 h-3.5 text-slate-400" />
                    {offer.pickupLocation}
                  </span>

                  <span className="flex items-center gap-1 text-slate-400">
                    <Clock className="w-3.5 h-3.5" />
                    Posted {new Date(offer.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>

              </div>
            );
          })}
        </div>
      )}

    </div>
  );
};

