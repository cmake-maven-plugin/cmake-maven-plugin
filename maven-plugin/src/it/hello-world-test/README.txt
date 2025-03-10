Hello-World-Test
================

This test is a basic test of the generator, compiler, tester and installer.  It is configured to
fail the build on a test failure, but the test returns a successful response.  The
process creates a separate local Maven repository that is used to run the test
projects.  It takes a bit longer because of this on the first time it's run (and after
a `clean` is run on the parent project).
