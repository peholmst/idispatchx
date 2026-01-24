# NFR: Maintainability

The expected lifespan of iDispatchX is 15 years. In order to ensure the code can be properly maintained for that long, the following guidelines apply:

## Dependencies

* Avoid all frameworks unless absolutely necessary and justified by platform constraints or long-term maintenance benefits.
* Prefer standard libraries and platform APIs to third party libraries.
  * Never add a third party library without careful consideration. If you only need a fraction of the library, consider implementing it yourself.

## Structure and Behavior

* Use modern programming language features wherever possible, as long as it does not affect the readability and understandability of the code in a negative way.
* Prefer composition to inheritance.
* Prefer explicitness to implicitness and "auto-magic", even if this means writing more code.

## Documentation

* Document all public APIs and SPIs, including intended audience and stability expectations, using the programming language's documentation format (Javadoc for Java for example).

## Testing

* Design the code in such a way that it can be easily tested with unit tests.
* Treat tests as specifications and documentation rather than a means of achieving coverage.
* Prefer unit tests to other types of automatic tests, but don't avoid writing other types of tests when appropriate.
