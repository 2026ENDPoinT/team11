module.exports = {
  preset: '@react-native/jest-preset',
  setupFiles: ['./jest.setup.js'],
  transformIgnorePatterns: ['node_modules/(?!(@react-native|react-native|@react-navigation|react-native-calendars|react-native-svg|@react-native-picker|@react-native-async-storage)/)'],
};
