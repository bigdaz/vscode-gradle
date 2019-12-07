module.exports = {
  singleQuote: true,
  tabWidth: 2,
  printWidth: 80,
  overrides: [
    {
      files: '*.svg',
      options: {
        parser: 'html'
      }
    }
  ]
};
