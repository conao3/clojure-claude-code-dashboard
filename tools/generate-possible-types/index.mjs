import { readFileSync, writeFileSync } from 'fs';
import { buildSchema } from 'graphql';

const SCHEMA_PATH = 'resources/schema.graphql';
const OUTPUT_PATH = 'resources/public/possibleTypes.json';

function main() {
  console.log('Generate possibleTypes.json');
  console.log('===========================\n');

  console.log(`Loading schema from ${SCHEMA_PATH}...`);
  const schemaSource = readFileSync(SCHEMA_PATH, 'utf-8');

  let schema;
  try {
    schema = buildSchema(schemaSource);
    console.log('Schema parsed successfully.\n');
  } catch (error) {
    console.error('Failed to parse schema:');
    console.error(error.message);
    process.exit(1);
  }

  const possibleTypes = {};

  const typeMap = schema.getTypeMap();
  for (const [typeName, type] of Object.entries(typeMap)) {
    if (typeName.startsWith('__')) continue;

    if (type.constructor.name === 'GraphQLInterfaceType') {
      const implementations = schema.getPossibleTypes(type);
      possibleTypes[typeName] = implementations.map(impl => impl.name);
      console.log(`Interface ${typeName}: [${possibleTypes[typeName].join(', ')}]`);
    }

    if (type.constructor.name === 'GraphQLUnionType') {
      const members = schema.getPossibleTypes(type);
      possibleTypes[typeName] = members.map(member => member.name);
      console.log(`Union ${typeName}: [${possibleTypes[typeName].join(', ')}]`);
    }
  }

  console.log(`\nWriting to ${OUTPUT_PATH}...`);
  writeFileSync(OUTPUT_PATH, JSON.stringify(possibleTypes, null, 2) + '\n');

  console.log('\n===========================');
  console.log('Generated successfully!');
}

main();
