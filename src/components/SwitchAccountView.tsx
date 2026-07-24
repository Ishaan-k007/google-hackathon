import React, { useState } from 'react';
import { Store, Building2, Check, Plus, UserCheck, MapPin, ShieldCheck } from 'lucide-react';
import { StoreAccount, FoodBankPartner, AccountRoleMode } from '../types';

interface SwitchAccountViewProps {
  stores: StoreAccount[];
  currentStore: StoreAccount;
  onSelectStore: (store: StoreAccount) => void;
  onAddStore: (newStore: StoreAccount) => void;
  onAddFoodBank?: (newFb: FoodBankPartner) => void;
  activeRole: AccountRoleMode;
  onSelectRole: (role: AccountRoleMode) => void;
}

export const SwitchAccountView: React.FC<SwitchAccountViewProps> = ({
  stores,
  currentStore,
  onSelectStore,
  onAddStore,
  onAddFoodBank,
  activeRole,
  onSelectRole,
}) => {
  const [showAddStoreModal, setShowAddStoreModal] = useState(false);
  const [showAddCharityModal, setShowAddCharityModal] = useState(false);
  const [charitySuccessMsg, setCharitySuccessMsg] = useState('');

  // Form states for Store
  const [newStoreName, setNewStoreName] = useState('');
  const [newStoreAddress, setNewStoreAddress] = useState('');
  const [newStoreDock, setNewStoreDock] = useState('');
  const [newCodePrefix, setNewCodePrefix] = useState('TESCO');

  // Form states for Charity
  const [newCharityName, setNewCharityName] = useState('');
  const [newCharityLocation, setNewCharityLocation] = useState('');

  const handleCreateStore = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newStoreName.trim() || !newStoreAddress.trim()) return;

    const created: StoreAccount = {
      id: `store-${Date.now()}`,
      name: newStoreName.trim(),
      address: newStoreAddress.trim(),
      dockInfo: newStoreDock.trim() || 'Loading Bay 01',
      defaultCodePrefix: newCodePrefix.trim().toUpperCase() || 'TESCO',
    };

    onAddStore(created);
    onSelectStore(created);
    onSelectRole('SUPERMARKET');
    setShowAddStoreModal(false);
    setNewStoreName('');
    setNewStoreAddress('');
    setNewStoreDock('');
  };

  const handleCreateCharity = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCharityName.trim() || !newCharityLocation.trim()) return;

    const created: FoodBankPartner = {
      id: `fb-${Date.now()}`,
      name: newCharityName.trim(),
      location: newCharityLocation.trim(),
    };

    if (onAddFoodBank) {
      onAddFoodBank(created);
    }
    setCharitySuccessMsg(`Successfully registered charity account "${created.name}"!`);
    setShowAddCharityModal(false);
    setNewCharityName('');
    setNewCharityLocation('');
  };

  return (
    <div className="space-y-6">
      
      {/* Header Banner */}
      <div className="bg-white rounded-lg border border-slate-200 p-6 shadow-2xs">
        <div className="flex items-center justify-between pb-4 border-b border-slate-100">
          <div>
            <h2 className="text-base sm:text-lg font-bold text-slate-900 flex items-center gap-2">
              <UserCheck className="w-5 h-5 text-slate-700" />
              Account & Store Switcher
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Switch active store location, register a new store, or onboard a new charity food bank.
            </p>
          </div>

          <span className="px-2.5 py-1 bg-slate-100 text-slate-800 border border-slate-200 rounded text-xs font-semibold">
            Active Store: {currentStore.name}
          </span>
        </div>

        {/* Section 1: Supermarket Store Locations */}
        <div className="pt-5 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-bold text-slate-600 uppercase tracking-wider flex items-center gap-1.5">
              <Store className="w-4 h-4 text-slate-600" />
              Select Active Store
            </h3>

            <button
              onClick={() => setShowAddStoreModal(true)}
              className="text-xs font-semibold text-slate-800 hover:text-slate-900 bg-slate-100 hover:bg-slate-200 px-3 py-1.5 rounded-md border border-slate-300 transition-all flex items-center gap-1"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Register New Store</span>
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {stores.map((store) => {
              const isSelected = activeRole === 'SUPERMARKET' && currentStore.id === store.id;

              return (
                <div
                  key={store.id}
                  onClick={() => {
                    onSelectStore(store);
                    onSelectRole('SUPERMARKET');
                  }}
                  className={`p-4 rounded-lg border cursor-pointer transition-all flex items-start justify-between gap-3 ${
                    isSelected
                      ? 'border-slate-800 bg-slate-50 ring-1 ring-slate-800 shadow-2xs'
                      : 'border-slate-200 bg-slate-50/50 hover:bg-white hover:border-slate-300'
                  }`}
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold text-slate-900">{store.name}</span>
                      {isSelected && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-slate-900 text-white flex items-center gap-0.5">
                          <Check className="w-3 h-3" /> Active Logged In
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-600 flex items-center gap-1">
                      <MapPin className="w-3 h-3 text-slate-400 shrink-0" />
                      {store.address}
                    </p>
                    <p className="text-[11px] text-slate-500 font-medium">
                      Dock: {store.dockInfo} • Code Prefix: {store.defaultCodePrefix}
                    </p>
                  </div>

                  {!isSelected && (
                    <button
                      type="button"
                      className="px-3 py-1.5 bg-white border border-slate-200 text-slate-700 text-xs font-semibold rounded-md hover:bg-slate-100 shrink-0"
                    >
                      Switch Store
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* Section 2: Charity Partner Prompt & Registration */}
        <div className="pt-6 border-t border-slate-100 mt-6 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-lg bg-slate-50 border border-slate-200">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <Building2 className="w-4 h-4 text-amber-700 shrink-0" />
                <h3 className="text-xs font-bold text-slate-900 uppercase tracking-wider">
                  Register a New Charity Account
                </h3>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed max-w-xl">
                Partner with local food banks or community kitchens to receive automated surplus notifications and direct collection access from your store branches.
              </p>
            </div>

            <button
              onClick={() => setShowAddCharityModal(true)}
              className="px-3.5 py-2 bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs rounded-md transition-all flex items-center gap-1.5 shrink-0 self-start sm:self-auto"
            >
              <Plus className="w-4 h-4" />
              <span>Register Charity Account</span>
            </button>
          </div>

          {charitySuccessMsg && (
            <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-md text-emerald-900 text-xs font-semibold flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>{charitySuccessMsg}</span>
            </div>
          )}
        </div>

      </div>

      {/* Modal: Register New Supermarket Store Account */}
      {showAddStoreModal && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-lg border border-slate-200 p-6 max-w-lg w-full shadow-lg space-y-5">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Store className="w-5 h-5 text-slate-700" />
                Register New Store
              </h3>
              <button
                onClick={() => setShowAddStoreModal(false)}
                className="text-slate-400 hover:text-slate-600 font-bold text-sm"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateStore} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1">
                  Store Branch Name
                </label>
                <input
                  type="text"
                  placeholder="e.g. Tesco Express - Islington"
                  value={newStoreName}
                  onChange={(e) => setNewStoreName(e.target.value)}
                  required
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-md text-sm font-medium text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1">
                  Street Address & Postcode
                </label>
                <input
                  type="text"
                  placeholder="e.g. 12 Upper Street, London N1 0PQ"
                  value={newStoreAddress}
                  onChange={(e) => setNewStoreAddress(e.target.value)}
                  required
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-md text-sm font-medium text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1">
                    Dock / Pickup Bay
                  </label>
                  <input
                    type="text"
                    placeholder="e.g. Loading Bay 01"
                    value={newStoreDock}
                    onChange={(e) => setNewStoreDock(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-md text-sm font-medium text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1">
                    Pickup Code Prefix
                  </label>
                  <input
                    type="text"
                    placeholder="e.g. TESCO"
                    value={newCodePrefix}
                    onChange={(e) => setNewCodePrefix(e.target.value.toUpperCase())}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-md text-sm font-mono font-bold text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800"
                  />
                </div>
              </div>

              <div className="pt-3 flex gap-3">
                <button
                  type="button"
                  onClick={() => setShowAddStoreModal(false)}
                  className="w-1/2 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold rounded-md text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="w-1/2 py-2.5 bg-slate-900 hover:bg-slate-800 text-white font-semibold rounded-md text-xs shadow-2xs"
                >
                  Create & Login
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Register New Charity Account */}
      {showAddCharityModal && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-lg border border-slate-200 p-6 max-w-lg w-full shadow-lg space-y-5">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Building2 className="w-5 h-5 text-amber-700" />
                Register New Charity Partner Account
              </h3>
              <button
                onClick={() => setShowAddCharityModal(false)}
                className="text-slate-400 hover:text-slate-600 font-bold text-sm"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateCharity} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1">
                  Charity / Food Bank Name
                </label>
                <input
                  type="text"
                  placeholder="e.g. St. Pancras Community Food Kitchen"
                  value={newCharityName}
                  onChange={(e) => setNewCharityName(e.target.value)}
                  required
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-md text-sm font-medium text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1">
                  Operating Address or Community District
                </label>
                <input
                  type="text"
                  placeholder="e.g. 14 Euston Road, London NW1 2AA"
                  value={newCharityLocation}
                  onChange={(e) => setNewCharityLocation(e.target.value)}
                  required
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-md text-sm font-medium text-slate-800 focus:bg-white focus:outline-none focus:ring-2 focus:ring-slate-800"
                />
              </div>

              <div className="pt-3 flex gap-3">
                <button
                  type="button"
                  onClick={() => setShowAddCharityModal(false)}
                  className="w-1/2 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold rounded-md text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="w-1/2 py-2.5 bg-slate-900 hover:bg-slate-800 text-white font-semibold rounded-md text-xs shadow-2xs"
                >
                  Register Charity Partner
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
};


