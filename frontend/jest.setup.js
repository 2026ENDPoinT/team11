// AsyncStorage v3는 jest mock을 안 싣는다 → 메모리 맵 몇 줄
const mockStore = new Map();
jest.mock('@react-native-async-storage/async-storage', () => ({
  __esModule: true,
  default: {
    getItem: async k => (mockStore.has(k) ? mockStore.get(k) : null),
    setItem: async (k, v) => void mockStore.set(k, v),
    removeItem: async k => void mockStore.delete(k),
    clear: async () => mockStore.clear(),
  },
}));
