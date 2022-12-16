#pragma once

#include <memory>

#include <jank/runtime/obj/list.hpp>
#include <jank/analyze/expression_base.hpp>

namespace jank::analyze::expr
{
  template <typename E>
  struct call : expression_base
  {
    /* Var, local, or callable. */
    std::shared_ptr<E> source_expr;
    runtime::obj::list_ptr args;
    std::vector<std::shared_ptr<E>> arg_exprs;
  };
}
