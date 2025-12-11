#!/usr/bin/env node

const path = require('path');

// Load the main backend script
require(path.join(__dirname, '..', 'resources', 'backend', 'main.js'));
