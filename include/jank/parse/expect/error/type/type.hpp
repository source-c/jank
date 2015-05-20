#pragma once

#include <stdexcept>
#include <string>

namespace jank
{
  namespace parse
  {
    namespace expect
    {
      namespace error
      {
        namespace type
        {
          struct generic
          { static char const constexpr *description{ "expression" }; };

          /* TODO: rename to exception. */
          template <typename T = generic>
          struct exception : std::runtime_error
          {
            exception()
              : std::runtime_error
                { std::string{ "type error (" } + T::description + ")" }
            { }
            exception(std::string const &s)
              : std::runtime_error
                { std::string{ "type error (" } + T::description + "): " + s }
            { }
          };
        }
      }
    }
  }
}
