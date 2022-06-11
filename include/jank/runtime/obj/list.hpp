#pragma once

#include <jank/runtime/object.hpp>
#include <jank/runtime/behavior/seq.hpp>
#include <jank/runtime/behavior/countable.hpp>

namespace jank::runtime::obj
{
  struct list : object, behavior::seqable, behavior::countable, pool_item_base<list>
  {
    list() = default;
    list(list &&) = default;
    list(list const &) = default;
    list(runtime::detail::list_type &&d)
      : data{ std::move(d) }
    { }
    list(runtime::detail::list_type const &d)
      : data{ d }
    { }

    static runtime::detail::box_type<list> create(runtime::detail::list_type const &l);

    runtime::detail::boolean_type equal(object const &) const override;
    runtime::detail::string_type to_string() const override;
    runtime::detail::integer_type to_hash() const override;

    list const* as_list() const override;
    behavior::seqable const* as_seqable() const override;

    behavior::sequence_pointer seq() const override;
    size_t count() const override;

    runtime::detail::list_type data;
  };
  using list_ptr = runtime::detail::box_type<list>;
}