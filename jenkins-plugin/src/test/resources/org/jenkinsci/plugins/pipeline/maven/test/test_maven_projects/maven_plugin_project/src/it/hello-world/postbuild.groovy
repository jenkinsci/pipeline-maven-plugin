file = new File(basedir, 'build.log');
assert file.exists();

assert file.text.matches('(?is).*hello(-maven-plugin)?:1.0-SNAPSHOT:hello \\(default\\) @ hello-maven-plugin-it.*');
assert file.text.contains('Hello, world.');

return true;
