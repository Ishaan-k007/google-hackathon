import React, { useState } from 'react';
import { Building2, MapPin, KeyRound, CheckCircle2, Hand, ShieldCheck, Clock, AlertCircle } from 'lucide-react';
import { FoodOffer, FoodBankPartner } from '../types';

interface FoodBankViewProps {
  offers: FoodOffer[];
  foodBanks: FoodBankPartner[];
  onClaimOffer: (offerId: string, foodBankName: string) => void;
  onCollectOffer: (offerId: string, inputCode: string) => boolean;
}

export const FoodBankView: React.FC<FoodBankViewProps> = ({
  offers,
  foodBanks,
  onClaimOffer,
  onCollectOffer,
}) => {
  const [selectedFoodBank, setSelectedFoodBank] = useState<string>(foodBanks[0]?.name || 'St. Mary Community Food Pantry');
  const [customBankName, setCustomBankName] = useState<string>('');
  const [codeInputMap, setCodeInputMap] = useState<Record<string, string>>({});
  const [codeErrorMap, setCodeErrorMap] = useState<Record<string, string>>({});
  const [filter, setFilter] = useState<'ALL' | 'AVAILABLE' | 'CLAIMED' | 'COLLECTED'>('ALL');

  const currentBankName = selectedFoodBank === 'CUSTOM' ? (customBankName.trim() || 'My Food Bank') : selectedFoodBank;

  const handleClaim = (offerId: string) => {
    onClaimOffer(offerId, currentBankName);
  };

  const handleVerifyCollect = (offerId: string) => {
    const inputCode = codeInputMap[offerId] || '';
    if (!inputCode.trim()) {
      setCodeErrorMap((prev) => ({ ...prev, [offerId]: 'Please enter the pickup code' }));
      return;
    }

    const success = onCollectOffer(offerId, inputCode.trim());
    if (!success) {
      setCodeErrorMap((prev) => ({ ...prev, [offerId]: 'Invalid code. Check code from supermarket.' }));
    } else {
      setCodeErrorMap((prev) => ({ ...prev, [offerId]: '' }));
      setCodeInputMap((prev) => ({ ...prev, [offerId]: '' }));
    }
  };

  const filteredOffers = offers.filter((offer) => {
    if (filter === 'ALL') return true;
    return offer.status === filter;
  });

  const availableCount = offers.filter((o) => o.status === 'AVAILABLE').length;
  const claimedCount = offers.filter((o) => o.status === 'CLAIMED').length;

  return (
    <div className="space-y-6">
      
      {/* Active Food Bank Identity Bar */}
      <div className="bg-white rounded-lg border border-slate-200 p-5 shadow-2xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-md bg-slate-100 text-slate-700 flex items-center justify-center font-bold shrink-0">
            <Building2 className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-900">Food Bank Charity Hub</h2>
            <p className="text-xs text-slate-500">
              Select your organisation to claim available supermarket surplus batches.
            </p>
          </div>
        </div>

        {/* Identity Selector */}
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
          <label className="text-xs font-semibold text-slate-600 whitespace-nowrap">
            Acting As:
          </label>
          <select
            value={selectedFoodBank}
            onChange={(e) => setSelectedFoodBank(e.target.value)}
            className="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-md text-xs font-semibold text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800"
          >
            {foodBanks.map((fb) => (
              <option key={fb.id} value={fb.name}>
                {fb.name}
              </option>
            ))}
            <option value="CUSTOM">+ Enter Custom Food Bank Name</option>
          </select>

          {selectedFoodBank === 'CUSTOM' && (
            <input
              type="text"
              placeholder="Your Charity / Shelter Name"
              value={customBankName}
              onChange={(e) => setCustomBankName(e.target.value)}
              className="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-md text-xs font-medium text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800"
            />
          )}
        </div>
      </div>

      {/* Offers Stream & Filter Header */}
      <div className="bg-white rounded-lg border border-slate-200 p-6 shadow-2xs">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 mb-5 border-b border-slate-100">
          <div>
            <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
              Supermarket Available Food Batches
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Claim items to notify other charities that your team is picking it up.
            </p>
          </div>

          {/* Filter Pills */}
          <div className="flex items-center gap-1 bg-slate-100 p-1 rounded-md border border-slate-200 self-start sm:self-auto">
            <button
              onClick={() => setFilter('ALL')}
              className={`px-2.5 py-1 rounded text-xs font-semibold transition-all ${
                filter === 'ALL' ? 'bg-white text-slate-900 shadow-2xs font-bold' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              All ({offers.length})
            </button>
            <button
              onClick={() => setFilter('AVAILABLE')}
              className={`px-2.5 py-1 rounded text-xs font-semibold transition-all ${
                filter === 'AVAILABLE' ? 'bg-emerald-700 text-white shadow-2xs font-bold' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Available ({availableCount})
            </button>
            <button
              onClick={() => setFilter('CLAIMED')}
              className={`px-2.5 py-1 rounded text-xs font-semibold transition-all ${
                filter === 'CLAIMED' ? 'bg-amber-600 text-white shadow-2xs font-bold' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Claimed ({claimedCount})
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

        {/* Offers Cards List */}
        {filteredOffers.length === 0 ? (
          <div className="py-12 text-center border border-dashed border-slate-200 rounded-lg p-6 space-y-2">
            <AlertCircle className="w-8 h-8 text-slate-300 mx-auto" />
            <p className="text-sm font-semibold text-slate-600">No surplus food batches match this filter.</p>
            <p className="text-xs text-slate-400">Supermarket posts will appear here automatically.</p>
          </div>
        ) : (
          <div className="space-y-3">
            {filteredOffers.map((offer) => {
              const isAvailable = offer.status === 'AVAILABLE';
              const isClaimedByMe = offer.status === 'CLAIMED' && offer.claimedBy === currentBankName;
              const isClaimedByOther = offer.status === 'CLAIMED' && offer.claimedBy !== currentBankName;
              const isCollected = offer.status === 'COLLECTED';

              return (
                <div
                  key={offer.id}
                  className={`p-4 rounded-lg border transition-all ${
                    isAvailable
                      ? 'border-slate-200 bg-white hover:border-slate-300'
                      : isClaimedByMe
                      ? 'border-amber-200 bg-amber-50/40'
                      : isClaimedByOther
                      ? 'border-slate-200 bg-slate-50/60 opacity-90'
                      : 'border-slate-200 bg-slate-100/50'
                  }`}
                >
                  <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                    
                    {/* Left Info Column */}
                    <div className="space-y-2 flex-grow">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-xs font-bold text-slate-900 bg-white px-2 py-0.5 rounded border border-slate-200">
                          {offer.storeName}
                        </span>
                        
                        {/* Status Badges */}
                        {isAvailable && (
                          <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                            Available for Rescue
                          </span>
                        )}
                        {offer.status === 'CLAIMED' && (
                          <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-amber-50 text-amber-800 border border-amber-200 flex items-center gap-1">
                            <Hand className="w-3 h-3 text-amber-600" />
                            Claimed by {offer.claimedBy}
                          </span>
                        )}
                        {isCollected && (
                          <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-200 flex items-center gap-1">
                            <CheckCircle2 className="w-3 h-3 text-slate-600" />
                            Collected & Verified
                          </span>
                        )}
                      </div>

                      {/* Food Surplus Description Box View */}
                      <div className="p-3 bg-slate-50 border border-slate-200 rounded-md text-xs text-slate-800 leading-relaxed font-medium">
                        {offer.description}
                      </div>

                      {/* Pickup Location & Time */}
                      <div className="flex flex-wrap items-center gap-4 text-xs text-slate-500 pt-0.5">
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

                    {/* Right Action Column */}
                    <div className="shrink-0 lg:w-72 flex flex-col justify-center border-t lg:border-t-0 lg:border-l border-slate-200 pt-3 lg:pt-0 lg:pl-5 space-y-3">
                      
                      {/* Case 1: Available -> Claim Button */}
                      {isAvailable && (
                        <button
                          onClick={() => handleClaim(offer.id)}
                          className="w-full py-2 px-4 bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs rounded-md transition-all flex items-center justify-center gap-2"
                        >
                          <Hand className="w-4 h-4" />
                          <span>Request / Claim Food</span>
                        </button>
                      )}

                      {/* Case 2: Claimed by another food bank */}
                      {isClaimedByOther && (
                        <div className="p-2.5 rounded-md bg-slate-100 text-slate-600 text-xs font-medium text-center">
                          🔒 Claimed by another charity.
                        </div>
                      )}

                      {/* Case 3: Claimed (by me or ready for pickup verification) */}
                      {(isClaimedByMe || (offer.status === 'CLAIMED' && !isClaimedByOther)) && (
                        <div className="space-y-2">
                          <div className="text-[11px] font-bold text-amber-900 flex items-center gap-1">
                            <KeyRound className="w-3.5 h-3.5 text-amber-600" />
                            Enter Pickup Code at Store:
                          </div>

                          <div className="flex gap-2">
                            <input
                              type="text"
                              placeholder="e.g. ALPHA-29"
                              value={codeInputMap[offer.id] || ''}
                              onChange={(e) =>
                                setCodeInputMap((prev) => ({ ...prev, [offer.id]: e.target.value.toUpperCase() }))
                              }
                              className="w-full px-3 py-1.5 bg-white border border-slate-300 rounded-md text-xs font-mono font-bold text-slate-800 uppercase focus:outline-none focus:ring-2 focus:ring-slate-800"
                            />
                            <button
                              onClick={() => handleVerifyCollect(offer.id)}
                              className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs rounded-md transition-all shrink-0"
                            >
                              Verify
                            </button>
                          </div>

                          {codeErrorMap[offer.id] && (
                            <p className="text-[11px] font-semibold text-red-600">{codeErrorMap[offer.id]}</p>
                          )}
                        </div>
                      )}

                      {/* Case 4: Collected */}
                      {isCollected && (
                        <div className="p-2.5 bg-slate-100 rounded-md border border-slate-200 text-center text-xs font-semibold text-slate-700 flex items-center justify-center gap-1.5">
                          <ShieldCheck className="w-4 h-4 text-slate-600" />
                          <span>Handoff Verified & Complete</span>
                        </div>
                      )}

                    </div>

                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

    </div>
  );
};

