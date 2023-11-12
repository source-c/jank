#pragma once

#include <jank/runtime/object.hpp>
#include <jank/runtime/detail/object_util.hpp>
#include <jank/runtime/obj/persistent_hash_map_sequence.hpp>
#include <jank/runtime/obj/detail/base_persistent_map.hpp>

namespace jank::runtime
{
  namespace obj
  {
    using persistent_array_map = static_object<object_type::persistent_array_map>;
    using persistent_array_map_ptr = native_box<persistent_array_map>;
  }

  template <>
  struct static_object<object_type::persistent_hash_map>
  : obj::detail::base_persistent_map
    <
      object_type::persistent_hash_map,
      object_type::persistent_hash_map_sequence,
      runtime::detail::native_persistent_hash_map
    >
  {
    static_object() = default;
    static_object(static_object &&) = default;
    static_object(static_object const &) = default;
    static_object(runtime::detail::native_array_map const &m, object_ptr key, object_ptr val);
    static_object(value_type &&d)
      : data{ std::move(d) }
    { }
    static_object(value_type const &d)
      : data{ d }
    { }
    template <typename... Args>
    static_object(runtime::detail::in_place_unique, Args &&...args)
      : data{ std::forward<Args>(args)... }
    { }

    using base_persistent_map::base_persistent_map;

    template <typename... Args>
    static native_box<static_object> create_unique(Args &&...args)
    {
      return make_box<static_object>
      (
        runtime::detail::in_place_unique{},
        std::forward<Args>(args)...
      );
    }

    /* behavior::associatively_readable */
    object_ptr get(object_ptr const key) const;
    object_ptr get(object_ptr const key, object_ptr const fallback) const;

    /* behavior::associatively_writable */
    object_ptr assoc(object_ptr key, object_ptr val) const;

    value_type data{};
  };

  namespace obj
  {
    using persistent_hash_map = static_object<object_type::persistent_hash_map>;
    using persistent_hash_map_ptr = native_box<persistent_hash_map>;
  }
}