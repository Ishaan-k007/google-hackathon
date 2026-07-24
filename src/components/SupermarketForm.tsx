import React, { useState, useEffect } from 'react';
import { MapPin, KeyRound, Send, RefreshCw, ArrowRight, CheckCircle2 } from 'lucide-react';
import { FoodOffer, StoreAccount } from '../types';

interface SupermarketFormProps {
  currentStore: StoreAccount;
  onAddOffer: (offer: Omit<FoodOffer, 'id' | 'createdAt' | 'status'>) => void;
  onNavigateToAvailableItems: () => void;
}

export const SupermarketForm: React.FC<SupermarketFormProps> = ({
  currentStore,
  onAddOffer,
  onNavigateToAvailableItems,
}) => {
  const [storeName, setStoreName] = useState(currentStore.name);
  const [pickupLocation, setPickupLocation] = useState(`${currentStore.address} (${currentStore.dockInfo})`);
  const [pickupCode, setPickupCode] = useState(`${currentStore.defaultCodePrefix}-29`);
  const [description, setDescription] = useState('');
  const [isSuccess, setIsSuccess] = useState(false);

  // Sync state if store account changes
  useEffect(() => {
    setStoreName(currentStore.name);
    setPickupLocation(`${currentStore.address} (${currentStore.dockInfo})`);
    setPickupCode(`${currentStore.defaultCodePrefix}-${Math.floor(10 + Math.random() * 89)}`);
  }, [currentStore]);

  const generateRandomCode = () => {
    const nums = Math.floor(10 + Math.random() * 89);
    setPickupCode(`${currentStore.defaultCodePrefix}-${nums}`);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!storeName.trim() || !pickupLocation.trim() || !description.trim()) return;

    onAddOffer({
      storeName,
      pickupLocation,
      pickupCode: pickupCode.trim() || `${currentStore.defaultCodePrefix}-01`,
      description,
    });

    setDescription('');
    setIsSuccess(true);
  };

  return (
    <div className="bg-white rounded-lg border border-slate-200 p-6 shadow-2xs space-y-6">
      
      {/* Header */}
      <div className="pb-4 border-b border-slate-100">
        <h2 className="text-base font-bold text-slate-900">
          Flag Surplus Food for Rescue
        </h2>
        <p className="text-xs text-slate-500 mt-0.5">
          Input pickup details, secret handover code, and food surplus description for charity collection.
        </p>
      </div>

      {/* Success Alert */}
      {isSuccess && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-md flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-emerald-900 text-xs font-medium">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>Surplus food batch successfully flagged and available to local food banks!</span>
          </div>
          <button
            onClick={onNavigateToAvailableItems}
            className="px-3 py-1.5 bg-emerald-700 hover:bg-emerald-800 text-white font-semibold rounded-md text-xs flex items-center gap-1 shrink-0 self-start sm:self-auto transition-all"
          >
            <span>View in Your Available Items</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Form */}
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          
          {/* Store Name Input */}
          <div>
            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1">
              Store Account
            </label>
            <input
              type="text"
              value={storeName}
              onChange={(e) => setStoreName(e.target.value)}
              placeholder="e.g. Tesco - Kings Cross"
              required
              className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-md text-sm font-medium text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800 transition-all"
            />
          </div>

          {/* Pickup Location Input */}
          <div>
            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1">
              Pickup Location & Dock Info
            </label>
            <div className="relative">
              <MapPin className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                value={pickupLocation}
                onChange={(e) => setPickupLocation(e.target.value)}
                placeholder="e.g. 21 Caledonian Rd, London N1 9DX (Bay 02)"
                required
                className="w-full pl-9 pr-3 py-2 bg-slate-50 border border-slate-200 rounded-md text-sm font-medium text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800 transition-all"
              />
            </div>
          </div>

          {/* Pickup Code Input */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider">
                Secret Pickup Code
              </label>
              <button
                type="button"
                onClick={generateRandomCode}
                className="text-[11px] font-semibold text-slate-600 hover:text-slate-900 flex items-center gap-1"
              >
                <RefreshCw className="w-3 h-3" />
                Randomize
              </button>
            </div>
            <div className="relative">
              <KeyRound className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                value={pickupCode}
                onChange={(e) => setPickupCode(e.target.value.toUpperCase())}
                placeholder="e.g. TESCO-29"
                required
                className="w-full pl-9 pr-3 py-2 bg-slate-50 border border-slate-200 rounded-md text-sm font-mono font-bold text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800 transition-all"
              />
            </div>
          </div>

        </div>

        {/* Viewable Food Description Box */}
        <div>
          <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1">
            Surplus Food Description
          </label>
          <textarea
            rows={4}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="e.g. Roughly 12 crates of organic apples, 15 artisan sourdough loaves baked this morning, and 8 gallons of milk expiring tomorrow at 5 PM. Ready for immediate charity pickup."
            required
            className="w-full p-3 bg-slate-50 border border-slate-200 rounded-md text-sm text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800 transition-all placeholder:text-slate-400"
          />
        </div>

        {/* Action Button */}
        <button
          type="submit"
          className="w-full py-2.5 bg-slate-900 hover:bg-slate-800 text-white font-semibold rounded-md shadow-2xs transition-all flex items-center justify-center gap-2 text-xs uppercase tracking-wider"
        >
          <Send className="w-4 h-4" />
          <span>Flag Available Surplus Food</span>
        </button>
      </form>
    </div>
  );
};

